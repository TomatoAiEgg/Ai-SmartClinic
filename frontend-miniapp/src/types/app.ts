export type AgentRoute = 'TRIAGE' | 'REGISTRATION' | 'GUIDE' | 'HUMAN_REVIEW'

export type MessageRole = 'user' | 'assistant' | 'system'

export type ActionTone = 'primary' | 'default' | 'danger'

export interface ChatPayload {
  chatId: string
  userId: string
  message: string
  metadata: Record<string, string>
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

export interface AppConfig {
  baseUrl: string
  userId: string
  chatId: string
}

export interface QuickAction {
  title: string
  description: string
  message: string
  metadata?: Record<string, string>
}
