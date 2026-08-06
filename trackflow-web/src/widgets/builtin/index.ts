/**
 * 内置 Widget 统一注册入口
 *
 * 每个 import 触发对应 Widget 的 defineWidget() 调用，自动注册到全局注册表。
 * 新增内置 Widget 只需在此处添加一行 import，无需修改其他文件。
 */
import './NoteWidget'
import './NumberCardWidget'
import './ReportChartWidget'
import './IssueListWidget'
import './ActivityFeedWidget'
import './SprintProgressWidget'
import './AgileChartWidget'
import './BoardStatusWidget'
import './CalendarWidget'
import './ProjectTeamWidget'
