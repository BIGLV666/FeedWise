// 状态/模块/来源的中文展示字典（与后端枚举一一对应）
export const ROLE_NAMES = { SUPPORT: '客服', PM: '产品经理', DEV: '开发/测试' }

export const FEEDBACK_STATUS = { UNPROCESSED: '未处理', PROCESSED: '已归档' }
export const ISSUE_STATUS = { PENDING_REVIEW: '待产品经理确认', CONFIRMED: '已确认', REJECTED: '已驳回', MERGED: '已合并' }
export const DRAFT_STATUS = { DRAFT: '草稿', CONFIRMED: '已确认', CONVERTED: '已转任务' }
export const TASK_STATUS = { TODO: '待开发', IN_PROGRESS: '开发中', PENDING_VERIFY: '待验证', DONE: '已完成' }
export const TASK_PRIORITY = { P1: 'P1 紧急', P2: 'P2 常规', P3: 'P3 低' }

export const MODULES = {
  REIMBURSE_FORM: '报销单填写',
  INVOICE_UPLOAD: '发票上传',
  APPROVAL: '审批进度',
  RETURN_MODIFY: '退回修改',
  OTHER: '其他'
}

export const SOURCES = { TICKET: '客服工单', SURVEY: '使用调查', CALL: '客户沟通' }

// 允许的任务事件（与 state-kit task 状态机一致）
export const TASK_EVENTS = [
  { event: 'START', label: '开始开发', from: 'TODO' },
  { event: 'SUBMIT', label: '提交验证', from: 'IN_PROGRESS' },
  { event: 'PASS', label: '验证通过', from: 'PENDING_VERIFY' },
  { event: 'REJECT', label: '验证不通过，退回', from: 'PENDING_VERIFY' }
]
