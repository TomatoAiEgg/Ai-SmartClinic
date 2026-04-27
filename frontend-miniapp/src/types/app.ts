export type AgentRoute = 'TRIAGE' | 'REGISTRATION' | 'GUIDE' | 'HUMAN_REVIEW'

export type MessageRole = 'user' | 'assistant' | 'system'

export type ActionTone = 'primary' | 'default' | 'danger'

export interface ChatPayload {
  chatId: string
  userId?: string
  message: string
  metadata: Record<string, string>
}

export interface LoginResponse {
  token: string
  userId: string
  expiresIn: number
  tokenHeader: string
}

export interface WechatLoginPayload {
  code: string
  nickname: string
  avatarUrl: string
}

export interface ChatApiResponse {
  chatId: string
  route: AgentRoute
  message: string
  requiresConfirmation: boolean
  data: Record<string, unknown>
}

export interface MessageAction {
  label: string
  message: string
  metadata?: Record<string, string>
  tone?: ActionTone
}

export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  createdAt: string
  route?: AgentRoute
  requiresConfirmation?: boolean
  data?: Record<string, unknown>
  actions?: MessageAction[]
}

export interface AppointmentRecord {
  registrationId: string
  status: string
  message: string
  patientId?: string
  departmentCode?: string
  doctorId?: string
  clinicDate?: string
  startTime?: string
  updatedAt: string
}

export interface PatientProfile {
  patientId: string
  userId: string
  name: string
  idType: string
  maskedIdNumber?: string
  maskedPhone?: string
  relationCode: string
  defaultPatient: boolean
}

export interface PatientCreatePayload {
  name: string
  idType: string
  idNumber: string
  phone: string
  relationCode: string
  defaultPatient: boolean
}

export interface WechatProfile {
  nickname: string
  avatarUrl: string
  loginCode: string
}

export interface AuthState {
  userId: string
  username: string
  authToken: string
  tokenHeader: string
  wechatNickname: string
  wechatAvatarUrl: string
  loginMode: 'none' | 'wechat'
}

export interface ChatSessionState {
  chatId: string
}

export interface QuickAction {
  title: string
  description: string
  message: string
  metadata?: Record<string, string>
}

export type AppRouteName = 'chat' | 'appointments' | 'guide' | 'profile' | 'patients'

export interface AppRoute {
  name: AppRouteName
  path: string
  title: string
  requiresAuth: boolean
  bottomNav: boolean
}
