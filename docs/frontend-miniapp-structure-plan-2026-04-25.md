# 前端小程序结构收口方案

## 1. 当前判断

`frontend-miniapp` 的表层目录结构是正常的：

- `pages`：页面
- `components`：复用组件
- `stores`：Pinia 状态
- `services`：接口封装
- `utils`：工具函数

真正的问题不在目录，而在运行时架构边界混乱。

当前前端本质上是：

- `uni-app + Vue 3 + Pinia`
- 运行目标以微信小程序为主
- 路由真实来源是 `pages.json`

但项目里又额外模拟了一层“类 Web Router + 类 TabBar 语义”，导致导航和鉴权行为反复打架。

## 2. 主要结构问题

### 2.1 路由真源重复

当前存在两套路由来源：

- `src/pages.json`
- `src/router/routes.ts`

其中：

- `pages.json` 是小程序真实路由配置
- `routes.ts` 只是额外维护的一份页面元数据

问题是 `routes.ts` 里的 `tabBar` 字段被用于运行时跳转判断，但它并不是微信原生 tabBar 配置。

直接结果：

- 容易误用 `switchTab`
- 容易误用 `reLaunch`
- 后续很容易继续出现“首页闪一下”“切页手感发硬”“页面栈异常”

### 2.2 自定义底部栏和原生 tabBar 语义混淆

当前底部栏是自定义组件 `AppTabBar.vue`，不是微信原生 tabBar。

这意味着：

- 如果想要原生 tab 稳定性，就必须改成原生 `custom tabBar + switchTab`
- 如果想要普通页面的滑动切页手感，就必须承认它只是普通页面导航，不能再套原生 tab 的语义

这两条路线必须二选一，不能混着做。

### 2.3 登录触发点过多

当前登录相关逻辑分散在：

- `App.vue`
- `router/guard.ts`
- `utils/auth.ts`
- `services/request.ts` 的拦截器
- 各页面 `onShow`

结果是：

- 登录弹窗触发时机不稳定
- 页面进入、请求发送、401 处理互相抢职责
- 很难判断“这次登录是谁发起的”

### 2.4 请求层掺入了 UI 行为

`request.ts` 和 `utils/auth.ts` 现在已经不只是基础设施层，还承担了：

- 登录弹窗
- loading
- 微信授权
- 过期重登

这会让请求层和 UI 层纠缠在一起，后面任何登录体验调整都会波及请求封装。

### 2.5 有 Web SPA 残影

依赖里还保留了 `vue-router`，但当前小程序并没有真正使用它。

这会误导后续维护者继续按 Web SPA 思路加抽象。

## 3. 收口目标

前端后续只保留一套清晰模型：

- `pages.json`：唯一页面路由真源
- `routes.ts`：只保留页面展示元数据，不决定跳转语义
- `navigation.ts`：只封装 uni-app 页面跳转 API，不再推断“伪 tab 逻辑”
- `guard.ts`：只负责页面访问权限判断
- `request.ts`：只负责请求、鉴权头、401 标准化
- `auth.ts`：只负责登录流程编排

## 4. 推荐路线

### 路线 A：稳定优先

改成微信原生 `custom tabBar + switchTab`。

优点：

- 最符合小程序模型
- 底部导航最稳
- 页面栈最可控

缺点：

- 没有普通页面那种“滑过去”的切页手感

### 路线 B：交互手感优先

保留自定义底部栏，但明确把它当成“普通页面导航”，不是 tabBar。

优点：

- 可以保留滑动切页体验
- 视觉和交互更自由

缺点：

- 页面栈需要自己兜底
- 返回行为要自己约束
- 不能再使用 `switchTab`

## 5. 本项目建议

结合当前前端已经是自定义底部栏、你又明确希望“点击底部导航更像普通页面滑过去”，建议选 **路线 B**。

也就是说，后面要明确承认：

- 这不是原生 tabBar
- 它只是一个自定义底部导航
- 相关页面都是普通页面

一旦确定这条路线，后面就不再允许：

- 把 `routes.ts` 里的 `tabBar` 当成原生 tab 语义
- 在 `navigation.ts` 里切回 `switchTab`
- 为了修体验再临时插入 `reLaunch`

## 6. 具体收口步骤

### 第一步：路由模型收正

- `pages.json` 继续做唯一页面路由源
- `routes.ts` 保留，但把 `tabBar` 重命名成 `bottomNav`
- `routes.ts` 只描述：
  - 页面名称
  - 页面标题
  - 是否要求登录
  - 是否展示在底部导航

不再让它决定：

- 用 `switchTab`
- 用 `reLaunch`
- 用 `navigateTo`

### 第二步：导航层收正

`navigation.ts` 只保留 3 类语义：

- `openPage`
- `replacePage`
- `openBottomPage`

其中：

- `openPage` -> `navigateTo`
- `replacePage` -> `redirectTo`
- `openBottomPage` -> 按路线 B 明确使用普通页面策略，并单独处理栈深与重复页面

禁止继续出现：

- `switchToRoute` 这种名义上像 tabBar、实际上不是 tabBar 的混合接口

### 第三步：登录入口收正

登录只保留一个“交互入口”：

- 页面进入受保护页面时，如果本地没有 token，则弹登录

其余层统一降级：

- `App.vue`：只做初始化，不主动弹 UI
- `guard.ts`：负责页面访问判断
- `request.ts`：只负责带 token 和处理 401
- `utils/auth.ts`：只负责编排微信登录流程

目标是以后每次出现登录弹窗，都能明确回答：

- 是页面 guard 触发的
- 不是请求层偷偷触发的

### 第四步：请求层降职责

`request.ts` 保留：

- base URL
- header
- token 注入
- 响应错误标准化

`request.ts` 不再负责：

- 主动拉起登录弹窗
- 主动执行微信授权 UI

401 的处理策略应改成：

- 清理本地 session
- 抛出统一错误
- 由上层决定是否弹登录

### 第五步：页面层去重复

各页面不要再同时做：

- `onShow -> requireAuthForPage`
- 按钮点击前再 `ensureLogin`
- 请求失败后再触发一轮登录

后面要统一模式：

- 页面进入时做一次权限检查
- 页面内用户主动动作，只在必要时校验当前登录态

## 7. 非阻塞问题

这些不是当前主因，但后面可以顺手清理：

- 删掉未使用的 `vue-router` 依赖
- 处理现有文件里的中文乱码
- 拆开 `chatStore -> appointmentsStore` 的跨域依赖
- 让 `chatStore.ensureWelcome()` 不再是空实现

## 8. 后续执行原则

后面前端调整按下面规则执行：

1. 先确定路由路线是 A 还是 B，再改代码。
2. 不再用“临时换一个跳转 API”方式试手感。
3. 任何导航改动都只允许落在 `navigation.ts` 和底部导航组件。
4. 任何登录体验改动都先确认职责属于 `App / guard / auth / request` 中哪一层。
5. 页面业务逻辑和导航/鉴权逻辑分开改，避免再次互相污染。

## 9. 当前结论

当前前端不是“目录结构错”，而是“运行时架构抽象错”。

一句话概括：

`这是一个 uni-app 小程序前端，不应该继续按 Web SPA 的路由心智去补抽象。`
