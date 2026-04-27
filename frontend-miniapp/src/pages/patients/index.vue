<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, reactive } from 'vue'
import AppTabBar from '@/components/AppTabBar.vue'
import { runProtectedPageTask } from '@/router/guard'
import { useAuthStore } from '@/stores/auth'
import { usePatientsStore } from '@/stores/patients'
import { ensureLogin } from '@/utils/auth'

const authStore = useAuthStore()
const patientsStore = usePatientsStore()

const relationOptions = [
  { label: '本人', value: 'SELF' },
  { label: '子女', value: 'CHILD' },
  { label: '父母', value: 'PARENT' },
  { label: '配偶', value: 'SPOUSE' },
  { label: '其他', value: 'OTHER' },
]

const form = reactive({
  name: '',
  idType: 'ID_CARD',
  idNumber: '',
  phone: '',
  relationIndex: 0,
  defaultPatient: true,
})

const patients = computed(() => patientsStore.items)
const loading = computed(() => patientsStore.loading)
const saving = computed(() => patientsStore.saving)
const relationLabel = computed(() => relationOptions[form.relationIndex]?.label || relationOptions[0].label)

onShow(() => {
  void runProtectedPageTask('/pages/patients/index', loadPatients)
})

async function loginNow() {
  if (await ensureLogin()) {
    await loadPatients()
  }
}

async function loadPatients(showSuccess = false) {
  if (!authStore.loggedIn) {
    return
  }
  try {
    await patientsStore.load(authStore.toRequestContext())
    if (showSuccess) {
      uni.showToast({
        title: '已刷新',
        icon: 'success',
      })
    }
  }
  catch (error) {
    uni.showModal({
      title: '加载失败',
      content: error instanceof Error ? error.message : '就诊人加载失败。',
      showCancel: false,
    })
  }
}

async function submitPatient() {
  if (!await ensureLogin()) {
    return
  }
  const name = form.name.trim()
  if (!name) {
    uni.showToast({
      title: '请填写姓名',
      icon: 'none',
    })
    return
  }

  try {
    await patientsStore.create({
      name,
      idType: form.idType,
      idNumber: form.idNumber.trim(),
      phone: form.phone.trim(),
      relationCode: relationOptions[form.relationIndex]?.value || 'SELF',
      defaultPatient: form.defaultPatient || patients.value.length === 0,
    }, authStore.toRequestContext())
    resetForm()
    uni.showToast({
      title: '已保存',
      icon: 'success',
    })
  }
  catch (error) {
    uni.showModal({
      title: '保存失败',
      content: error instanceof Error ? error.message : '就诊人保存失败。',
      showCancel: false,
    })
  }
}

async function setDefault(patientId: string) {
  if (!await ensureLogin()) {
    return
  }
  try {
    await patientsStore.setDefault(patientId, authStore.toRequestContext())
    uni.showToast({
      title: '已设为默认',
      icon: 'success',
    })
  }
  catch (error) {
    uni.showModal({
      title: '设置失败',
      content: error instanceof Error ? error.message : '默认就诊人设置失败。',
      showCancel: false,
    })
  }
}

function updateField(field: 'name' | 'idNumber' | 'phone', event: { detail: { value: string } }) {
  form[field] = event.detail.value
}

function updateRelation(event: { detail: { value: string | number } }) {
  form.relationIndex = Number(event.detail.value || 0)
}

function updateDefault(event: { detail: { value: boolean } }) {
  form.defaultPatient = Boolean(event.detail.value)
}

function resetForm() {
  form.name = ''
  form.idNumber = ''
  form.phone = ''
  form.relationIndex = 0
  form.defaultPatient = patients.value.length === 0
}
</script>

<template>
  <view class="page-shell patients-page">
    <view class="header glass-card">
      <text class="section-title">就诊人</text>
      <button v-if="authStore.loggedIn" class="refresh-btn" :disabled="loading" @tap="loadPatients(true)">
        {{ loading ? '刷新中' : '刷新' }}
      </button>
    </view>

    <view v-if="!authStore.loggedIn" class="empty glass-card">
      <text class="empty-title">请先登录</text>
      <button class="primary-btn" @tap="loginNow">微信登录</button>
    </view>

    <template v-else>
      <view class="form-panel glass-card">
        <view class="field">
          <text class="field-label">姓名</text>
          <input class="field-input" :value="form.name" :maxlength="32" @input="updateField('name', $event)">
        </view>

        <view class="field-row">
          <view class="field grow">
            <text class="field-label">证件类型</text>
            <view class="field-input static">身份证</view>
          </view>
          <view class="field grow">
            <text class="field-label">关系</text>
            <picker :range="relationOptions" range-key="label" :value="form.relationIndex" @change="updateRelation">
              <view class="field-input static">{{ relationLabel }}</view>
            </picker>
          </view>
        </view>

        <view class="field">
          <text class="field-label">证件号码</text>
          <input class="field-input" :value="form.idNumber" :maxlength="32" @input="updateField('idNumber', $event)">
        </view>

        <view class="field">
          <text class="field-label">手机号</text>
          <input class="field-input" :value="form.phone" type="number" :maxlength="18" @input="updateField('phone', $event)">
        </view>

        <view class="switch-line">
          <text class="field-label">默认就诊人</text>
          <switch :checked="form.defaultPatient || patients.length === 0" color="#237bff" @change="updateDefault" />
        </view>

        <button class="primary-btn" :disabled="saving || !form.name.trim()" @tap="submitPatient">
          {{ saving ? '保存中' : '保存就诊人' }}
        </button>
      </view>

      <view v-if="loading && !patients.length" class="empty glass-card">
        <text class="empty-title">正在加载</text>
      </view>

      <view v-else-if="patients.length" class="patient-list">
        <view v-for="patient in patients" :key="patient.patientId" class="patient-card glass-card">
          <view class="patient-main">
            <view class="name-line">
              <text class="patient-name">{{ patient.name }}</text>
              <text v-if="patient.defaultPatient" class="default-tag">默认</text>
            </view>
            <text class="patient-meta">{{ patient.idType }} {{ patient.maskedIdNumber || '未填写证件' }}</text>
            <text class="patient-meta">手机号 {{ patient.maskedPhone || '未填写' }}</text>
          </view>
          <button v-if="!patient.defaultPatient" class="ghost-btn" :disabled="saving" @tap="setDefault(patient.patientId)">
            设为默认
          </button>
        </view>
      </view>

      <view v-else class="empty glass-card">
        <text class="empty-title">暂无就诊人</text>
      </view>
    </template>

    <AppTabBar current="profile" />
  </view>
</template>

<style scoped lang="scss">
.patients-page {
  padding-bottom: 260rpx;
}

.header,
.form-panel,
.empty,
.patient-card {
  padding: 30rpx;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.form-panel {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
  margin-top: 24rpx;
}

.field-row {
  display: flex;
  gap: 18rpx;
}

.field,
.grow {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10rpx;
  min-width: 0;
}

.field-label {
  font-size: 24rpx;
  color: #6f839d;
}

.field-input {
  min-height: 84rpx;
  padding: 0 20rpx;
  border-radius: 22rpx;
  background: rgba(35, 123, 255, 0.06);
  color: #12253f;
  font-size: 26rpx;
  line-height: 84rpx;
}

.static {
  display: flex;
  align-items: center;
}

.switch-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.primary-btn,
.refresh-btn,
.ghost-btn {
  margin: 0;
  border-radius: 22rpx;
  font-size: 26rpx;
  line-height: 84rpx;
}

.primary-btn {
  background: #237bff;
  color: #fff;
}

.refresh-btn,
.ghost-btn {
  padding: 0 28rpx;
  background: rgba(35, 123, 255, 0.08);
  color: #237bff;
}

.primary-btn[disabled],
.refresh-btn[disabled],
.ghost-btn[disabled] {
  opacity: 0.55;
}

.patient-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
}

.patient-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22rpx;
}

.patient-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  min-width: 0;
}

.name-line {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.patient-name {
  color: #12253f;
  font-size: 32rpx;
  font-weight: 700;
}

.default-tag {
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(35, 123, 255, 0.1);
  color: #237bff;
  font-size: 20rpx;
}

.patient-meta {
  color: #6f839d;
  font-size: 24rpx;
  line-height: 1.5;
}

.empty {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  align-items: flex-start;
  margin-top: 24rpx;
}

.empty-title {
  color: #12253f;
  font-size: 32rpx;
  font-weight: 700;
}
</style>
