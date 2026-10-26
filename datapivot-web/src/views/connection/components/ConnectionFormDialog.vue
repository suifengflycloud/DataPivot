<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { createConnection, testConnectionByForm, updateConnection } from '@/api/connection'
import { DB_TYPE_OPTIONS, dbTypeOption } from '@/constants'
import type { ConnectionForm, ConnectionVO, DbTypeCode, TestResult } from '@/types/model'

const props = defineProps<{
  modelValue: boolean
  /** 传入则为编辑模式 */
  connection: ConnectionVO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved'): void
}>()

const isEdit = computed(() => props.connection !== null)

const formRef = ref<FormInstance>()
const saving = ref(false)
const testing = ref(false)
const testResult = ref<TestResult | null>(null)
/** 用户是否手动改过 URL（改过则不再自动拼接） */
const urlManuallyEdited = ref(false)

const initialForm = (): ConnectionForm => ({
  connName: '',
  dbType: 'MYSQL',
  host: '127.0.0.1',
  port: 3306,
  dbName: '',
  username: '',
  password: '',
  driverClass: 'com.mysql.cj.jdbc.Driver',
  jdbcUrl: '',
  extraParams: '',
  remark: '',
})

const form = reactive<ConnectionForm>(initialForm())

const rules: FormRules = {
  connName: [{ required: true, message: '请输入连接名称', trigger: 'blur' }],
  dbType: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  host: [{ required: true, message: '请输入IP地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  dbName: [{ required: true, message: '请输入数据库名/SID', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  jdbcUrl: [{ required: true, message: 'JDBC URL 不能为空', trigger: 'blur' }],
}

/** 按当前类型/主机/端口/库名自动拼接 URL */
function buildUrl(): string {
  if (typeof form.dbType !== 'string' || form.dbType === '') {
    return ''
  }
  const option = dbTypeOption(form.dbType as DbTypeCode)
  if (!form.host || !form.port || !form.dbName) {
    return form.jdbcUrl
  }
  let url = option.urlTemplate
    .replace('{host}', form.host)
    .replace('{port}', String(form.port))
    .replace('{db}', form.dbName)
  // 扩展参数追加到 URL 查询串
  const extra = form.extraParams.trim()
  if (extra) {
    url += (url.includes('?') ? '&' : '?') + extra
  }
  return url
}

/** 切换数据库类型：带出默认端口/驱动并重置 URL 自动拼接 */
function onTypeChange(code: DbTypeCode): void {
  const option = dbTypeOption(code)
  form.port = option.defaultPort
  form.driverClass = option.defaultDriver
  urlManuallyEdited.value = false
  form.jdbcUrl = buildUrl()
}

/** 表单字段变化时自动刷新 URL（未手动编辑过时） */
function refreshUrl(): void {
  if (!urlManuallyEdited.value) {
    form.jdbcUrl = buildUrl()
  }
}

/** 弹窗打开时初始化表单 */
function onOpen(): void {
  testResult.value = null
  urlManuallyEdited.value = false
  Object.assign(form, initialForm())
  if (props.connection) {
    Object.assign(form, {
      connName: props.connection.connName,
      dbType: props.connection.dbType,
      host: props.connection.host,
      port: props.connection.port,
      dbName: props.connection.dbName,
      username: props.connection.username,
      // 编辑模式密码留空表示不修改
      password: '',
      driverClass: props.connection.driverClass,
      jdbcUrl: props.connection.jdbcUrl,
      extraParams: props.connection.extraParams ?? '',
      remark: props.connection.remark ?? '',
    })
  } else {
    form.jdbcUrl = buildUrl()
  }
  formRef.value?.clearValidate()
}

/** 测试当前表单连通性 */
async function handleTest(): Promise<void> {
  if (!form.host || !form.port || !form.dbName || !form.username || !form.jdbcUrl) {
    ElMessage.warning('请先填写 IP、端口、数据库名、用户名与 JDBC URL')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testConnectionByForm({ ...form, password: form.password || '' })
  } finally {
    testing.value = false
  }
}

/** 保存连接 */
async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!isEdit.value && !form.password) {
    ElMessage.warning('新建连接必须填写密码')
    return
  }
  saving.value = true
  try {
    if (isEdit.value && props.connection) {
      await updateConnection(props.connection.id, { ...form })
      ElMessage.success('连接更新成功')
    } else {
      await createConnection({ ...form })
      ElMessage.success('连接创建成功')
    }
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}

function handleClose(): void {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑连接' : '新增连接'"
    width="640px"
    top="6vh"
    destroy-on-close
    @open="onOpen"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="连接名称" prop="connName">
            <el-input v-model="form.connName" placeholder="自定义别名，如 prod-mysql" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数据库类型" prop="dbType">
            <el-select v-model="form.dbType" style="width: 100%" @change="onTypeChange">
              <el-option
                v-for="option in DB_TYPE_OPTIONS"
                :key="option.code"
                :value="option.code"
                :label="option.label"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="IP地址" prop="host">
            <el-input v-model="form.host" placeholder="127.0.0.1" @input="refreshUrl" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="端口" prop="port">
            <el-input-number
              v-model="form.port"
              :min="1"
              :max="65535"
              :controls="false"
              style="width: 100%"
              @change="refreshUrl"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="数据库名/SID" prop="dbName">
            <el-input v-model="form.dbName" placeholder="库名 / SID / schema" @input="refreshUrl" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="数据库用户名" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          :placeholder="isEdit ? `留空表示不修改（当前: ${connection?.passwordMasked ?? ''}）` : '数据库密码'"
        />
      </el-form-item>
      <el-form-item label="驱动类名">
        <el-input v-model="form.driverClass" placeholder="按类型自动带出，可修改" />
      </el-form-item>
      <el-form-item label="JDBC URL" prop="jdbcUrl">
        <div style="width: 100%; display: flex; gap: 8px">
          <el-input v-model="form.jdbcUrl" placeholder="自动拼接，可手动修改" @input="urlManuallyEdited = true" />
          <el-button
            title="恢复自动拼接"
            @click="
              () => {
                urlManuallyEdited = false
                form.jdbcUrl = buildUrl()
              }
            "
          >
            重置
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="扩展参数">
        <el-input
          v-model="form.extraParams"
          placeholder="可选，如 connectTimeout=5000，将追加到 URL"
          @input="refreshUrl"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="300" show-word-limit />
      </el-form-item>
    </el-form>

    <!-- 测试结果 -->
    <el-alert
      v-if="testResult"
      :title="testResult.success ? `连接成功（${testResult.costMs}ms）` : testResult.message"
      :description="testResult.success ? testResult.dbProduct ?? '' : testResult.message"
      :type="testResult.success ? 'success' : 'error'"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button :loading="testing" @click="handleTest">测试连接</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>
