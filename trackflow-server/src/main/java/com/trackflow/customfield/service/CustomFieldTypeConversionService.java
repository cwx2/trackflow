package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.customfield.entity.*;
import com.trackflow.customfield.mapper.*;
import com.trackflow.customfield.vo.AvailableConversionsVO;
import com.trackflow.customfield.vo.ConversionResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义字段类型转换服务
 * <p>
 * 按照 YouTrack 的转换规则实现字段类型之间的转换。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldTypeConversionService {

    private final CustomFieldDefinitionMapper definitionMapper;
    private final CustomFieldValueMapper valueMapper;
    private final CustomFieldOptionMapper optionMapper;

    /**
     * 类型转换规则映射表（sourceFormat -> Set<targetFormat>）
     */
    private static final Map<String, Set<String>> CONVERSION_RULES = new HashMap<>();

    static {
        // string 可转换为 text 或 list
        CONVERSION_RULES.put("string", Set.of("text", "list"));

        // text 可转换为 string
        CONVERSION_RULES.put("text", Set.of("string"));

        // date 可与 datetime 互换
        CONVERSION_RULES.put("date", Set.of("datetime"));
        CONVERSION_RULES.put("datetime", Set.of("date"));

        // int/float/period 可互换
        CONVERSION_RULES.put("int", Set.of("float", "period"));
        CONVERSION_RULES.put("float", Set.of("int", "period"));
        CONVERSION_RULES.put("period", Set.of("int", "float"));

        // list 可转换为 string（多值会变成逗号分隔字符串）
        CONVERSION_RULES.put("list", Set.of("string"));

        // ownedField 可转换为 list（丢弃 owner 信息）或 string
        CONVERSION_RULES.put("ownedField", Set.of("list", "string"));

        // version 可转换为 list（丢弃 releaseDate/released 信息）或 string
        CONVERSION_RULES.put("version", Set.of("list", "string"));

        // bool 和 user 类型没有可用转换
        CONVERSION_RULES.put("bool", Set.of());
        CONVERSION_RULES.put("user", Set.of());
    }

    /**
     * 获取字段可用的类型转换选项
     *
     * @param fieldId 字段 ID
     * @return 可用转换选项
     */
    public AvailableConversionsVO getAvailableConversions(Long fieldId) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "自定义字段不存在");
        }

        AvailableConversionsVO vo = new AvailableConversionsVO();
        vo.setCurrentFormat(field.getFieldFormat());

        // 检查是否被看板使用
        String blockedReason = checkBoardUsage(fieldId, field.getName());
        if (blockedReason != null) {
            vo.setConversionAllowed(false);
            vo.setBlockedReason(blockedReason);
            vo.setAvailableTargets(Collections.emptyList());
            return vo;
        }

        vo.setConversionAllowed(true);

        Set<String> targets = CONVERSION_RULES.getOrDefault(field.getFieldFormat(), Set.of());
        List<AvailableConversionsVO.ConversionOption> options = new ArrayList<>();

        for (String target : targets) {
            AvailableConversionsVO.ConversionOption opt = new AvailableConversionsVO.ConversionOption();
            opt.setFormat(target);
            opt.setDisplayName(getFormatDisplayName(target));

            // int/float -> period 需要指定单位
            if (("int".equals(field.getFieldFormat()) || "float".equals(field.getFieldFormat()))
                    && "period".equals(target)) {
                opt.setRequiresOptions(true);
                opt.setOptions(List.of("MINUTES", "HOURS", "DAYS"));
            }

            // period -> int/float 需要指定单位
            if ("period".equals(field.getFieldFormat())
                    && ("int".equals(target) || "float".equals(target))) {
                opt.setRequiresOptions(true);
                opt.setOptions(List.of("MINUTES", "HOURS", "DAYS"));
            }

            // list -> string 会丢失颜色和描述
            if ("list".equals(field.getFieldFormat()) && "string".equals(target)) {
                opt.setWarning("转换后将丢失选项的颜色、描述等属性，且无法恢复原有选项集");
            }

            // string -> list 需要创建选项
            if ("string".equals(field.getFieldFormat()) && "list".equals(target)) {
                opt.setWarning("转换后将为每个唯一值创建一个选项");
            }

            // datetime -> date 会丢失时间部分
            if ("datetime".equals(field.getFieldFormat()) && "date".equals(target)) {
                opt.setWarning("转换后将丢失时间部分，仅保留日期");
            }

            // float -> int 会丢失小数部分
            if ("float".equals(field.getFieldFormat()) && "int".equals(target)) {
                opt.setWarning("转换后将丢失小数部分（截断）");
            }

            options.add(opt);
        }

        vo.setAvailableTargets(options);
        return vo;
    }

    /**
     * 执行字段类型转换
     *
     * @param fieldId      字段 ID
     * @param targetFormat 目标类型
     * @param periodUnit   数值转换单位（MINUTES/HOURS/DAYS），仅 int/float <-> period 转换时使用
     * @return 转换结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ConversionResultVO convertFieldType(Long fieldId, String targetFormat, String periodUnit) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "自定义字段不存在");
        }

        // 内置字段不允许类型转换
        if (CustomFieldService.BUILTIN_FIELD_IDS.contains(fieldId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内置字段不允许类型转换");
        }

        String sourceFormat = field.getFieldFormat();

        // 校验转换合法性
        Set<String> allowedTargets = CONVERSION_RULES.getOrDefault(sourceFormat, Set.of());
        if (!allowedTargets.contains(targetFormat)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    String.format("不支持从 %s 转换为 %s", sourceFormat, targetFormat));
        }

        // 检查看板绑定
        String blockedReason = checkBoardUsage(fieldId, field.getName());
        if (blockedReason != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, blockedReason);
        }

        // 获取所有值
        List<CustomFieldValue> values = valueMapper.selectList(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getCustomFieldId, fieldId));

        Set<Long> affectedIssueIds = new HashSet<>();
        int convertedCount = 0;
        int failedCount = 0;
        StringBuilder warning = new StringBuilder();

        // 根据转换类型执行不同逻辑
        switch (sourceFormat + "->" + targetFormat) {
            case "string->text":
            case "text->string":
                // 直接转换，无需修改值
                convertedCount = values.size();
                affectedIssueIds.addAll(values.stream().map(CustomFieldValue::getIssueId).collect(Collectors.toSet()));
                break;

            case "string->list":
                convertedCount = convertStringToList(fieldId, values, affectedIssueIds);
                break;

            case "list->string":
                convertedCount = convertListToString(fieldId, values, affectedIssueIds);
                warning.append("选项颜色和描述已丢失。");
                break;

            case "date->datetime":
                convertedCount = convertDateToDatetime(values, affectedIssueIds);
                break;

            case "datetime->date":
                convertedCount = convertDatetimeToDate(values, affectedIssueIds);
                warning.append("时间部分已丢失。");
                break;

            case "int->float":
                // 直接转换，int 的值在 float 中完全兼容
                convertedCount = values.size();
                affectedIssueIds.addAll(values.stream().map(CustomFieldValue::getIssueId).collect(Collectors.toSet()));
                break;

            case "float->int":
                Object[] floatToIntResult = convertFloatToInt(values, affectedIssueIds);
                convertedCount = (int) floatToIntResult[0];
                failedCount = (int) floatToIntResult[1];
                if (failedCount > 0) {
                    warning.append(String.format("小数部分已截断。%d 个值转换失败。", failedCount));
                } else {
                    warning.append("小数部分已截断。");
                }
                break;

            case "int->period":
            case "float->period":
                convertedCount = convertNumberToPeriod(values, affectedIssueIds, periodUnit);
                break;

            case "period->int":
            case "period->float":
                convertedCount = convertPeriodToNumber(values, affectedIssueIds, periodUnit, "int".equals(targetFormat));
                break;

            default:
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        String.format("不支持的转换: %s -> %s", sourceFormat, targetFormat));
        }

        // 更新字段定义
        field.setFieldFormat(targetFormat);

        // 如果从 list 转换为非 list，清除 isMulti 标记
        if ("list".equals(sourceFormat) && !"list".equals(targetFormat)) {
            field.setIsMulti(false);
        }

        // 如果转换为 list，需要将值中的 is_multi 设为 false（单值）
        if ("list".equals(targetFormat) && !"list".equals(sourceFormat)) {
            field.setIsMulti(false); // 默认单选
            valueMapper.update(null,
                    new LambdaUpdateWrapper<CustomFieldValue>()
                            .eq(CustomFieldValue::getCustomFieldId, fieldId)
                            .set(CustomFieldValue::getIsMulti, false));
        }

        definitionMapper.updateById(field);

        log.info("字段类型转换完成: fieldId={}, {} -> {}, 影响 {} 条 issue, 转换 {} 个值",
                fieldId, sourceFormat, targetFormat, affectedIssueIds.size(), convertedCount);

        ConversionResultVO result = new ConversionResultVO();
        result.setFieldId(String.valueOf(fieldId));
        result.setFieldName(field.getName());
        result.setFromFormat(sourceFormat);
        result.setToFormat(targetFormat);
        result.setAffectedIssueCount(affectedIssueIds.size());
        result.setConvertedValueCount(convertedCount);
        result.setFailedValueCount(failedCount);
        result.setWarning(warning.length() > 0 ? warning.toString() : null);

        return result;
    }

    /**
     * 检查字段是否被看板使用
     *
     * @return 阻止原因，null 表示可以转换
     */
    private String checkBoardUsage(Long fieldId, String fieldName) {
        // 当前看板系统使用 columnField 字段存储字符串值（如 "status"、"priority"）
        // 而不是直接引用自定义字段 ID，因此自定义字段不会被看板直接用作列分组字段。
        // 
        // 未来如果扩展看板支持按自定义字段分列（column_field 存储 "cf_{fieldId}"），
        // 需要在这里添加检查逻辑。
        //
        // 泳道配置同样使用固定值（如 assignee/priority/type/sprint/tag/parent），
        // 不直接引用自定义字段 ID。

        return null;
    }

    /**
     * string -> list 转换：为每个唯一值创建选项
     */
    private int convertStringToList(Long fieldId, List<CustomFieldValue> values, Set<Long> affectedIssueIds) {
        // 收集所有唯一值
        Set<String> uniqueValues = values.stream()
                .map(CustomFieldValue::getValue)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.toSet());

        // 为每个唯一值创建选项
        int position = 0;
        Map<String, Long> valueToOptionId = new HashMap<>();
        for (String value : uniqueValues) {
            CustomFieldOption option = new CustomFieldOption();
            option.setCustomFieldId(fieldId);
            option.setValue(value);
            option.setPosition(position++);
            option.setIsDefault(false);
            optionMapper.insert(option);
            valueToOptionId.put(value, option.getId());
        }

        // 更新值为选项 ID
        for (CustomFieldValue cfv : values) {
            if (cfv.getValue() != null && !cfv.getValue().isBlank()) {
                Long optionId = valueToOptionId.get(cfv.getValue());
                if (optionId != null) {
                    cfv.setValue(String.valueOf(optionId));
                    cfv.setIsMulti(false);
                    valueMapper.updateById(cfv);
                    affectedIssueIds.add(cfv.getIssueId());
                }
            }
        }

        return values.size();
    }

    /**
     * list -> string 转换：将选项 ID 转换回选项值文本
     */
    private int convertListToString(Long fieldId, List<CustomFieldValue> values, Set<Long> affectedIssueIds) {
        // 获取所有选项映射
        List<CustomFieldOption> options = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId));
        Map<String, String> optionIdToValue = options.stream()
                .collect(Collectors.toMap(
                        opt -> String.valueOf(opt.getId()),
                        CustomFieldOption::getValue,
                        (a, b) -> a));

        // 处理多值字段：按 issueId 分组
        Map<Long, List<CustomFieldValue>> valuesByIssue = values.stream()
                .collect(Collectors.groupingBy(CustomFieldValue::getIssueId));

        int converted = 0;
        for (Map.Entry<Long, List<CustomFieldValue>> entry : valuesByIssue.entrySet()) {
            Long issueId = entry.getKey();
            List<CustomFieldValue> issueValues = entry.getValue();

            // 将多个选项值合并为逗号分隔字符串
            String combinedValue = issueValues.stream()
                    .map(CustomFieldValue::getValue)
                    .filter(Objects::nonNull)
                    .map(optionIdToValue::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));

            // 删除所有旧值，保留第一个更新
            if (!issueValues.isEmpty()) {
                CustomFieldValue firstValue = issueValues.get(0);
                firstValue.setValue(combinedValue);
                firstValue.setIsMulti(null); // string 类型不需要 is_multi
                valueMapper.updateById(firstValue);
                converted++;

                // 删除其他值
                for (int i = 1; i < issueValues.size(); i++) {
                    valueMapper.deleteById(issueValues.get(i).getId());
                }

                affectedIssueIds.add(issueId);
            }
        }

        // 删除所有选项
        optionMapper.delete(new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, fieldId));

        return converted;
    }

    /**
     * date -> datetime 转换：添加默认时间 00:00:00
     */
    private int convertDateToDatetime(List<CustomFieldValue> values, Set<Long> affectedIssueIds) {
        int converted = 0;
        for (CustomFieldValue cfv : values) {
            if (cfv.getValue() != null && !cfv.getValue().isBlank()) {
                try {
                    // 尝试解析为日期
                    LocalDate date = LocalDate.parse(cfv.getValue());
                    // 转换为 datetime
                    LocalDateTime dateTime = date.atStartOfDay();
                    cfv.setValue(dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    valueMapper.updateById(cfv);
                    converted++;
                    affectedIssueIds.add(cfv.getIssueId());
                } catch (DateTimeParseException e) {
                    log.warn("日期解析失败: value={}, issueId={}", cfv.getValue(), cfv.getIssueId());
                }
            }
        }
        return converted;
    }

    /**
     * datetime -> date 转换：截断时间部分
     */
    private int convertDatetimeToDate(List<CustomFieldValue> values, Set<Long> affectedIssueIds) {
        int converted = 0;
        for (CustomFieldValue cfv : values) {
            if (cfv.getValue() != null && !cfv.getValue().isBlank()) {
                try {
                    LocalDateTime dateTime = LocalDateTime.parse(cfv.getValue());
                    cfv.setValue(dateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    valueMapper.updateById(cfv);
                    converted++;
                    affectedIssueIds.add(cfv.getIssueId());
                } catch (DateTimeParseException e) {
                    // 尝试只有日期的格式
                    try {
                        LocalDate date = LocalDate.parse(cfv.getValue());
                        cfv.setValue(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
                        valueMapper.updateById(cfv);
                        converted++;
                        affectedIssueIds.add(cfv.getIssueId());
                    } catch (DateTimeParseException e2) {
                        log.warn("日期时间解析失败: value={}, issueId={}", cfv.getValue(), cfv.getIssueId());
                    }
                }
            }
        }
        return converted;
    }

    /**
     * float -> int 转换：截断小数
     */
    private Object[] convertFloatToInt(List<CustomFieldValue> values, Set<Long> affectedIssueIds) {
        int converted = 0;
        int failed = 0;
        for (CustomFieldValue cfv : values) {
            if (cfv.getValue() != null && !cfv.getValue().isBlank()) {
                try {
                    BigDecimal decimal = new BigDecimal(cfv.getValue());
                    cfv.setValue(String.valueOf(decimal.intValue()));
                    valueMapper.updateById(cfv);
                    converted++;
                    affectedIssueIds.add(cfv.getIssueId());
                } catch (NumberFormatException e) {
                    log.warn("数值解析失败: value={}, issueId={}", cfv.getValue(), cfv.getIssueId());
                    failed++;
                }
            }
        }
        return new Object[]{converted, failed};
    }

    /**
     * int/float -> period 转换：根据单位转换为分钟数
     */
    private int convertNumberToPeriod(List<CustomFieldValue> values, Set<Long> affectedIssueIds, String periodUnit) {
        int multiplier = switch (periodUnit == null ? "MINUTES" : periodUnit.toUpperCase()) {
            case "HOURS" -> 60;
            case "DAYS" -> 480; // 8小时/天
            default -> 1; // MINUTES
        };

        int converted = 0;
        for (CustomFieldValue cfv : values) {
            if (cfv.getValue() != null && !cfv.getValue().isBlank()) {
                try {
                    BigDecimal decimal = new BigDecimal(cfv.getValue());
                    int minutes = decimal.multiply(BigDecimal.valueOf(multiplier)).intValue();
                    cfv.setValue(String.valueOf(minutes));
                    valueMapper.updateById(cfv);
                    converted++;
                    affectedIssueIds.add(cfv.getIssueId());
                } catch (NumberFormatException e) {
                    log.warn("数值解析失败: value={}, issueId={}", cfv.getValue(), cfv.getIssueId());
                }
            }
        }
        return converted;
    }

    /**
     * period -> int/float 转换：根据单位从分钟数转换
     */
    private int convertPeriodToNumber(List<CustomFieldValue> values, Set<Long> affectedIssueIds,
                                       String periodUnit, boolean toInt) {
        int divisor = switch (periodUnit == null ? "MINUTES" : periodUnit.toUpperCase()) {
            case "HOURS" -> 60;
            case "DAYS" -> 480;
            default -> 1;
        };

        int converted = 0;
        for (CustomFieldValue cfv : values) {
            if (cfv.getValue() != null && !cfv.getValue().isBlank()) {
                try {
                    int minutes = Integer.parseInt(cfv.getValue());
                    if (toInt) {
                        cfv.setValue(String.valueOf(minutes / divisor));
                    } else {
                        cfv.setValue(String.valueOf((double) minutes / divisor));
                    }
                    valueMapper.updateById(cfv);
                    converted++;
                    affectedIssueIds.add(cfv.getIssueId());
                } catch (NumberFormatException e) {
                    log.warn("Period 值解析失败: value={}, issueId={}", cfv.getValue(), cfv.getIssueId());
                }
            }
        }
        return converted;
    }

    private String getFormatDisplayName(String format) {
        return switch (format) {
            case "string" -> "文本（单行）";
            case "text" -> "文本（多行）";
            case "int" -> "整数";
            case "float" -> "浮点数";
            case "date" -> "日期";
            case "datetime" -> "日期时间";
            case "period" -> "时间周期";
            case "bool" -> "布尔值";
            case "list" -> "枚举列表";
            case "user" -> "用户";
            case "version" -> "版本";
            default -> format;
        };
    }
}
