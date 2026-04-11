import type { AppointmentRecord, ChatApiResponse } from '@/types/app'
import { defineStore } from 'pinia'
import { STORAGE_KEYS, readStorage, writeStorage } from '@/utils/storage'

export const useAppointmentsStore = defineStore('appointments', {
  state: () => ({
    records: readStorage<AppointmentRecord[]>(STORAGE_KEYS.appointments, []),
  }),
  getters: {
    items(state) {
      return [...state.records].sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
    },
  },
  actions: {
    upsert(record: AppointmentRecord) {
      const index = this.records.findIndex(item => item.registrationId === record.registrationId)
      if (index >= 0) {
        this.records.splice(index, 1, { ...this.records[index], ...record })
      }
      else {
        this.records.unshift(record)
      }
      this.persist()
    },
    syncFromResponse(response: ChatApiResponse) {
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
      this.persist()
    },
    persist() {
      writeStorage(STORAGE_KEYS.appointments, this.records)
    },
  },
})

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
