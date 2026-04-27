import type { AppointmentRecord, ChatApiResponse } from '@/types/app'
import type { RequestAuthContext } from '@/services/request'
import { defineStore } from 'pinia'
import { fetchAppointment, fetchAppointments } from '@/services/appointments'

export const useAppointmentsStore = defineStore('appointments', {
  state: () => ({
    records: [] as AppointmentRecord[],
    loading: false,
    error: '',
  }),
  getters: {
    items(state) {
      return [...state.records].sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
    },
  },
  actions: {
    async load(authContext: RequestAuthContext) {
      this.loading = true
      this.error = ''
      try {
        this.records = await fetchAppointments(authContext)
        return this.records
      }
      catch (error) {
        this.error = readErrorMessage(error)
        throw error
      }
      finally {
        this.loading = false
      }
    },
    async refreshOne(registrationId: string, authContext: RequestAuthContext) {
      const record = await fetchAppointment(registrationId, authContext)
      this.upsert(record)
      return record
    },
    upsert(record: AppointmentRecord) {
      const index = this.records.findIndex(item => item.registrationId === record.registrationId)
      if (index >= 0) {
        this.records.splice(index, 1, { ...this.records[index], ...record })
      }
      else {
        this.records.unshift(record)
      }
    },
    syncFromResponse(response: ChatApiResponse) {
      const responseRecords = readRecords(response.data?.records)
      if (responseRecords.length) {
        this.records = responseRecords
        return
      }

      const registrationId = readString(response.data?.registrationId)
      if (!registrationId) {
        return
      }

      this.upsert({
        registrationId,
        status: readString(response.data?.status) || 'UNKNOWN',
        message: response.message,
        patientId: readString(response.data?.patientId),
        departmentCode: readString(response.data?.departmentCode),
        doctorId: readString(response.data?.doctorId),
        clinicDate: readString(response.data?.clinicDate),
        startTime: readString(response.data?.startTime),
        updatedAt: formatTimestamp(new Date()),
      })
    },
    clear() {
      this.records = []
    },
  },
})

function readRecords(value: unknown) {
  if (!Array.isArray(value)) {
    return []
  }
  return value.map(toAppointmentRecord).filter(isAppointmentRecord)
}

function toAppointmentRecord(value: unknown): AppointmentRecord | undefined {
  const item = value && typeof value === 'object' ? value as Record<string, unknown> : {}
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

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function readErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '预约记录加载失败。'
}

function formatTimestamp(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}`
}
