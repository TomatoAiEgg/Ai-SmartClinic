import type { AppointmentRecord } from '@/types/app'
import type { RequestAuthContext } from '@/services/request'
import { apiRequest } from '@/services/request'

interface RegistrationSearchBody {
  records?: unknown[]
}

export async function fetchAppointments(authContext: RequestAuthContext) {
  const body = await apiRequest<RegistrationSearchBody>({
    path: '/api/registrations',
    method: 'GET',
    authContext,
  })

  return readArray(body.records).map(toAppointmentRecord).filter(isAppointmentRecord)
}

export async function fetchAppointment(registrationId: string, authContext: RequestAuthContext) {
  const body = await apiRequest<Record<string, unknown>>({
    path: `/api/registrations/${encodeURIComponent(registrationId)}`,
    method: 'GET',
    authContext,
  })

  const record = toAppointmentRecord(body)
  if (!record) {
    throw new Error('预约详情响应缺少 registrationId。')
  }
  return record
}

function toAppointmentRecord(value: unknown): AppointmentRecord | undefined {
  const item = readRecord(value)
  const registrationId = readString(item.registrationId)
  if (!registrationId) {
    return undefined
  }

  return {
    registrationId,
    status: readString(item.status) || 'UNKNOWN',
    message: readString(item.message) || '已从后端同步预约记录。',
    patientId: readString(item.patientId),
    departmentCode: readString(item.departmentCode),
    doctorId: readString(item.doctorId),
    clinicDate: readString(item.clinicDate),
    startTime: readString(item.startTime),
    updatedAt: formatTimestamp(new Date()),
  }
}

function isAppointmentRecord(value: AppointmentRecord | undefined): value is AppointmentRecord {
  return Boolean(value)
}

function readArray(value: unknown) {
  return Array.isArray(value) ? value : []
}

function readRecord(value: unknown) {
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
}

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function formatTimestamp(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}`
}
