package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.handler.*;
import com.trackflow.customfield.mapper.CustomFieldOptionMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomFieldValidationEngineTest {

    @Mock
    private CustomFieldOptionMapper optionMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProjectMemberMapper projectMemberMapper;

    private CustomFieldValidationEngine engine;

    @BeforeEach
    void setUp() {
        // 构建所有 Handler 实例
        List<CustomFieldTypeHandler> handlers = List.of(
                new StringFieldHandler(),
                new TextFieldHandler(),
                new IntFieldHandler(),
                new FloatFieldHandler(),
                new DateFieldHandler(),
                new DateTimeFieldHandler(),
                new BoolFieldHandler(),
                new ListFieldHandler(optionMapper),
                new StateFieldHandler(optionMapper),
                new OwnedFieldHandler(optionMapper),
                new VersionFieldHandler(optionMapper),
                new UserFieldHandler(userMapper, projectMemberMapper),
                new PeriodFieldHandler()
        );
        CustomFieldHandlerRegistry registry = new CustomFieldHandlerRegistry(handlers);
        engine = new CustomFieldValidationEngine(registry);
    }

    private CustomFieldDefinition field(String name, String format) {
        CustomFieldDefinition f = new CustomFieldDefinition();
        f.setId(1L);
        f.setName(name);
        f.setFieldFormat(format);
        f.setIsRequired(false);
        f.setMinLength(0);
        f.setMaxLength(0);
        return f;
    }

    // ========== Required field tests ==========

    @Nested
    @DisplayName("Required field validation")
    class RequiredTests {
        @Test
        void required_field_with_null_value_returns_error() {
            CustomFieldDefinition f = field("Priority", "string");
            f.setIsRequired(true);

            var errors = engine.validate(f, null);
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getField()).isEqualTo("Priority");
            assertThat(errors.get(0).getMessage()).contains("必填");
        }

        @Test
        void required_field_with_blank_value_returns_error() {
            CustomFieldDefinition f = field("Priority", "string");
            f.setIsRequired(true);

            var errors = engine.validate(f, "   ");
            assertThat(errors).hasSize(1);
        }

        @Test
        void required_field_with_value_passes() {
            CustomFieldDefinition f = field("Priority", "string");
            f.setIsRequired(true);

            var errors = engine.validate(f, "High");
            assertThat(errors).isEmpty();
        }

        @Test
        void optional_field_with_null_value_passes() {
            CustomFieldDefinition f = field("Notes", "string");
            f.setIsRequired(false);

            var errors = engine.validate(f, null);
            assertThat(errors).isEmpty();
        }
    }

    // ========== Int validation ==========

    @Nested
    @DisplayName("Int format validation")
    class IntTests {
        @Test
        void valid_integer_passes() {
            var errors = engine.validate(field("Count", "int"), "42");
            assertThat(errors).isEmpty();
        }

        @Test
        void negative_integer_passes() {
            var errors = engine.validate(field("Count", "int"), "-7");
            assertThat(errors).isEmpty();
        }

        @Test
        void non_integer_fails() {
            var errors = engine.validate(field("Count", "int"), "abc");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("整数");
        }

        @Test
        void float_string_fails_int_validation() {
            var errors = engine.validate(field("Count", "int"), "3.14");
            assertThat(errors).hasSize(1);
        }
    }

    // ========== Float validation ==========

    @Nested
    @DisplayName("Float format validation")
    class FloatTests {
        @Test
        void valid_float_passes() {
            var errors = engine.validate(field("Hours", "float"), "3.14");
            assertThat(errors).isEmpty();
        }

        @Test
        void integer_as_float_passes() {
            var errors = engine.validate(field("Hours", "float"), "42");
            assertThat(errors).isEmpty();
        }

        @Test
        void non_numeric_fails() {
            var errors = engine.validate(field("Hours", "float"), "abc");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("数字");
        }
    }

    // ========== Date validation ==========

    @Nested
    @DisplayName("Date format validation")
    class DateTests {
        @Test
        void valid_iso_date_passes() {
            var errors = engine.validate(field("DueDate", "date"), "2025-07-11");
            assertThat(errors).isEmpty();
        }

        @Test
        void invalid_date_format_fails() {
            var errors = engine.validate(field("DueDate", "date"), "11/07/2025");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("yyyy-MM-dd");
        }

        @Test
        void non_date_string_fails() {
            var errors = engine.validate(field("DueDate", "date"), "not-a-date");
            assertThat(errors).hasSize(1);
        }
    }

    // ========== Bool validation ==========

    @Nested
    @DisplayName("Bool format validation")
    class BoolTests {
        @Test
        void true_passes() {
            var errors = engine.validate(field("Active", "bool"), "true");
            assertThat(errors).isEmpty();
        }

        @Test
        void false_passes() {
            var errors = engine.validate(field("Active", "bool"), "false");
            assertThat(errors).isEmpty();
        }

        @Test
        void other_string_fails() {
            var errors = engine.validate(field("Active", "bool"), "yes");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("true 或 false");
        }
    }

    // ========== String length/regexp validation ==========

    @Nested
    @DisplayName("String constraints validation")
    class StringTests {
        @Test
        void string_within_length_passes() {
            CustomFieldDefinition f = field("Code", "string");
            f.setMinLength(2);
            f.setMaxLength(10);

            var errors = engine.validate(f, "ABC");
            assertThat(errors).isEmpty();
        }

        @Test
        void string_too_short_fails() {
            CustomFieldDefinition f = field("Code", "string");
            f.setMinLength(3);
            f.setMaxLength(10);

            var errors = engine.validate(f, "AB");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("长度");
        }

        @Test
        void string_too_long_fails() {
            CustomFieldDefinition f = field("Code", "string");
            f.setMinLength(0);
            f.setMaxLength(5);

            var errors = engine.validate(f, "ABCDEFGHIJ");
            assertThat(errors).hasSize(1);
        }

        @Test
        void string_matching_regexp_passes() {
            CustomFieldDefinition f = field("Version", "string");
            f.setRegexp("\\d+\\.\\d+\\.\\d+");

            var errors = engine.validate(f, "1.2.3");
            assertThat(errors).isEmpty();
        }

        @Test
        void string_not_matching_regexp_fails() {
            CustomFieldDefinition f = field("Version", "string");
            f.setRegexp("\\d+\\.\\d+\\.\\d+");

            var errors = engine.validate(f, "abc");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("不匹配");
        }
    }

    // ========== List validation ==========

    @Nested
    @DisplayName("List format validation")
    class ListTests {
        @Test
        void valid_option_id_passes() {
            when(optionMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

            var errors = engine.validate(field("Severity", "list"), "123");
            assertThat(errors).isEmpty();
        }

        @Test
        void non_existent_option_id_fails() {
            when(optionMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);

            var errors = engine.validate(field("Severity", "list"), "999");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("无效的选项");
        }

        @Test
        void non_numeric_option_id_fails() {
            var errors = engine.validate(field("Severity", "list"), "abc");
            assertThat(errors).hasSize(1);
        }
    }

    // ========== User validation ==========

    @Nested
    @DisplayName("User format validation")
    class UserTests {
        @Test
        void valid_user_id_passes() {
            SysUser user = new SysUser();
            user.setId(42L);
            when(userMapper.selectById(42L)).thenReturn(user);

            var errors = engine.validate(field("Reviewer", "user"), "42");
            assertThat(errors).isEmpty();
        }

        @Test
        void non_existent_user_id_fails() {
            when(userMapper.selectById(999L)).thenReturn(null);

            var errors = engine.validate(field("Reviewer", "user"), "999");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("无效的用户");
        }

        @Test
        void non_numeric_user_id_fails() {
            var errors = engine.validate(field("Reviewer", "user"), "abc");
            assertThat(errors).hasSize(1);
        }
    }
}
