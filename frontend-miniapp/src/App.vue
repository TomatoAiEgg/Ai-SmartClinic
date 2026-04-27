<script lang="ts">
import { installAuthInterceptors } from '@/utils/auth'

export default {
  onLaunch() {
    installAuthInterceptors()
    console.log('AI挂号小程序已启动')
  },
}
</script>

<style lang="scss">
page {
  background:
    radial-gradient(circle at top, rgba(35, 123, 255, 0.14), transparent 42%),
    linear-gradient(180deg, #f4f8ff 0%, #eef4fb 100%);
  color: #12253f;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

view,
text,
button,
input,
textarea,
scroll-view {
  box-sizing: border-box;
}

button::after {
  border: none;
}
</style>
