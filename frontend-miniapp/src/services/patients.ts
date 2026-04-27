import type { PatientCreatePayload, PatientProfile } from '@/types/app'
import type { RequestAuthContext } from '@/services/request'
import { apiRequest } from '@/services/request'

interface PatientListBody {
  patients?: unknown[]
}

export async function fetchPatients(authContext: RequestAuthContext) {
  const body = await apiRequest<PatientListBody>({
    path: '/api/patients',
    method: 'GET',
    authContext,
  })

  return readArray(body.patients).map(toPatientProfile).filter(isPatientProfile)
}

export async function createPatient(payload: PatientCreatePayload, authContext: RequestAuthContext) {
  const body = await apiRequest<Record<string, unknown>>({
    path: '/api/patients',
    method: 'POST',
    data: payload,
    authContext,
  })

  const patient = toPatientProfile(body)
  if (!patient) {
    throw new Error('就诊人创建响应缺少 patientId。')
  }
  return patient
}

export async function setDefaultPatient(patientId: string, authContext: RequestAuthContext) {
  const body = await apiRequest<Record<string, unknown>>({
    path: `/api/patients/${encodeURIComponent(patientId)}/default`,
    method: 'POST',
    authContext,
  })

  const patient = toPatientProfile(body)
  if (!patient) {
    throw new Error('默认就诊人响应缺少 patientId。')
  }
  return patient
}

function toPatientProfile(value: unknown): PatientProfile | undefined {
  const item = readRecord(value)
  const patientId = readString(item.patientId)
  const userId = readString(item.userId)
  const name = readString(item.name)
  if (!patientId || !userId || !name) {
    return undefined
  }

  return {
    patientId,
    userId,
    name,
    idType: readString(item.idType) || 'ID_CARD',
    maskedIdNumber: readString(item.maskedIdNumber),
    maskedPhone: readString(item.maskedPhone),
    relationCode: readString(item.relationCode) || 'SELF',
    defaultPatient: Boolean(item.defaultPatient),
  }
}

function isPatientProfile(value: PatientProfile | undefined): value is PatientProfile {
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
