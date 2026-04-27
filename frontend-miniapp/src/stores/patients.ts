import type { PatientCreatePayload, PatientProfile } from '@/types/app'
import type { RequestAuthContext } from '@/services/request'
import { defineStore } from 'pinia'
import { createPatient, fetchPatients, setDefaultPatient } from '@/services/patients'

export const usePatientsStore = defineStore('patients', {
  state: () => ({
    records: [] as PatientProfile[],
    loading: false,
    saving: false,
    error: '',
  }),
  getters: {
    items(state) {
      return [...state.records].sort((left, right) => Number(right.defaultPatient) - Number(left.defaultPatient))
    },
    defaultPatient(state) {
      return state.records.find(patient => patient.defaultPatient)
    },
  },
  actions: {
    async load(authContext: RequestAuthContext) {
      this.loading = true
      this.error = ''
      try {
        this.records = await fetchPatients(authContext)
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
    async create(payload: PatientCreatePayload, authContext: RequestAuthContext) {
      this.saving = true
      try {
        const patient = await createPatient(payload, authContext)
        this.upsert(patient)
        if (patient.defaultPatient) {
          this.markDefault(patient.patientId)
        }
        return patient
      }
      finally {
        this.saving = false
      }
    },
    async setDefault(patientId: string, authContext: RequestAuthContext) {
      this.saving = true
      try {
        const patient = await setDefaultPatient(patientId, authContext)
        this.markDefault(patient.patientId)
        this.upsert(patient)
        return patient
      }
      finally {
        this.saving = false
      }
    },
    upsert(patient: PatientProfile) {
      const index = this.records.findIndex(item => item.patientId === patient.patientId)
      if (index >= 0) {
        this.records.splice(index, 1, { ...this.records[index], ...patient })
      }
      else {
        this.records.unshift(patient)
      }
    },
    markDefault(patientId: string) {
      this.records = this.records.map(patient => ({
        ...patient,
        defaultPatient: patient.patientId === patientId,
      }))
    },
    clear() {
      this.records = []
      this.error = ''
    },
  },
})

function readErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '就诊人加载失败。'
}
