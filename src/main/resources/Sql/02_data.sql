SET NAMES utf8mb4;

-- FeedWise 演示数据（密码统一 123456，BCrypt 哈希固定）

INSERT INTO fw_user (id, username, password, display_name, role, dept) VALUES
(1, 'support1', '$2a$10$CjwQyqux17Z7LxEdSKZNaOMbvi4UEhvjJ2scY.4KTMgdu17EK5ONK', '客服小王', 'SUPPORT', '反馈组'),
(2, 'support2', '$2a$10$CjwQyqux17Z7LxEdSKZNaOMbvi4UEhvjJ2scY.4KTMgdu17EK5ONK', '客服小李', 'SUPPORT', '客诉组'),
(3, 'pm',       '$2a$10$CjwQyqux17Z7LxEdSKZNaOMbvi4UEhvjJ2scY.4KTMgdu17EK5ONK', '王产品',   'PM',       '产品部'),
(4, 'dev1',     '$2a$10$CjwQyqux17Z7LxEdSKZNaOMbvi4UEhvjJ2scY.4KTMgdu17EK5ONK', '张工',     'DEV',      '研发部'),
(5, 'lead1',    '$2a$10$CjwQyqux17Z7LxEdSKZNaOMbvi4UEhvjJ2scY.4KTMgdu17EK5ONK', '客服主管赵姐', 'SUPPORT_LEAD', '反馈组');

-- 种子反馈：覆盖"发票上传失败"多种说法、混合反馈、含义不同但关键词相似、其他模块
INSERT INTO feedback (id, content, source, module, customer_tag, status, created_by) VALUES
(1,  '上传发票的时候一直转圈，最后提示上传失败，换了浏览器也不行', 'TICKET',  'INVOICE_UPLOAD', '客户A', 'UNPROCESSED', 1),
(2,  '发票传不上去，点上传按钮没反应，重装了APP还是一样',           'SURVEY',  'INVOICE_UPLOAD', '客户B', 'UNPROCESSED', 1),
(3,  '一上传发票就报错，错误码500，这周提交报销全卡在这',           'TICKET',  'INVOICE_UPLOAD', '客户C', 'UNPROCESSED', 2),
(4,  'PDF格式的发票上传失败，JPG的可以，怀疑是格式校验有问题',       'CALL',    'INVOICE_UPLOAD', '客户D', 'UNPROCESSED', 2),
(5,  '上传发票总是失败，而且报错之后也看不到报销单被退回的原因，两头都堵', 'TICKET', 'INVOICE_UPLOAD', '客户E', 'UNPROCESSED', 1),
(6,  '审批太麻烦了，一个报销单要三个人批，能不能简化一下流程',       'SURVEY',  'APPROVAL',       '客户F', 'UNPROCESSED', 2),
(7,  '审批进度看不到，只能干等，希望显示当前卡在哪个节点',           'TICKET',  'APPROVAL',       '客户G', 'UNPROCESSED', 1),
(8,  '找不到报销单退回原因，单子被打回来了都不知道哪里错了',         'TICKET',  'RETURN_MODIFY',  '客户H', 'UNPROCESSED', 1),
(9,  '报销单退回了，但是系统里看不到退回原因，只能去问审批人',       'SURVEY',  'RETURN_MODIFY',  '客户I', 'UNPROCESSED', 2),
(10, '找不到报销单填写入口，新版本改版之后菜单里翻不到',             'TICKET',  'REIMBURSE_FORM', '客户J', 'UNPROCESSED', 2),
(11, '报销单填写的时候金额字段不能粘贴，手输容易错',                 'SURVEY',  'REIMBURSE_FORM', '客户K', 'UNPROCESSED', 1),
(12, '导出的报销明细Excel打开是乱码',                               'CALL',    'OTHER',          '客户L', 'UNPROCESSED', 2);
