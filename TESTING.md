# 测试指南 (Testing Guide)

本项目使用 JUnit 5 + MockK 作为单元测试框架，使用 AndroidX Test 进行仪器测试。

## 1. 测试分类

### 单元测试 (`src/test/`)
- **使用内存模拟**：MockK 模拟依赖
- **无需 Android 设备**：在 JVM 上运行，速度快
- **测试范围**：ViewModel、Repository、工具类逻辑

### 仪器测试 (`src/androidTest/`)
- **使用真实环境**：Android 设备或模拟器
- **需要 Android 框架**：测试数据库、Service、UI
- **测试范围**：Room 数据库、AccessibilityService、UI 交互

---

## 2. 快速开始

### 前置条件

确保项目已正确配置测试依赖：

```kotlin
// build.gradle.kts (app module)
dependencies {
    // 单元测试
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")  // Flow 测试

    // 仪器测试
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
```

### 运行单元测试

```bash
# 使用 Gradle 命令
./gradlew test

# 运行特定测试类
./gradlew test --tests "com.aivoice.input.viewmodel.MainViewModelTest"

# 生成测试覆盖率报告
./gradlew testDebugUnitTestCoverage
```

### 运行仪器测试

```bash
# 连接设备或启动模拟器后运行
./gradlew connectedAndroidTest

# 运行特定测试类
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aivoice.input.db.RecordDaoTest
```

---

## 3. 测试环境配置

### 单元测试配置

```kotlin
// src/test/java/com/aivoice/input/TestConfig.kt
object TestConfig {
    // 测试用模拟数据
    const val TEST_AUDIO_PATH = "/mock/audio.pcm"
    const val TEST_ORIGINAL_TEXT = "今天天气不错"
    const val TEST_POLISHED_TEXT = "今天天气不错。"

    // 模拟 API 响应
    fun mockAsrResponse(text: String) = AsrResponse(
        isSuccess = true,
        text = text,
        code = 0,
        message = "success"
    )

    fun mockAiResponse(original: String, polished: String) = AiPolishResponse(
        isSuccess = true,
        originalText = original,
        polishedText = polished,
        message = "success"
    )
}
```

### 仪器测试配置

```kotlin
// src/androidTest/java/com/aivoice/input/TestApplication.kt
class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 使用内存数据库进行测试
        val testDb = Room.inMemoryDatabaseBuilder(
            this,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }
}
```

---

## 4. 测试最佳实践

### 4.1 ViewModel 测试

```kotlin
// src/test/java/com/aivoice/input/viewmodel/MainViewModelTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var viewModel: MainViewModel
    private val asrRepository: AsrRepository = mockk()
    private val aiRepository: AiRepository = mockk()
    private val recordRepository: RecordRepository = mockk()

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel(asrRepository, aiRepository, recordRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `processAudio should update result on success`() = runTest {
        // Given
        val audioPath = "/mock/audio.pcm"
        val originalText = "今天天气不错"
        val polishedText = "今天天气不错。"

        coEvery { asrRepository.recognize(audioPath) } returns Result.Success(originalText)
        coEvery { aiRepository.polish(originalText, PolishStyle.FORMAL) } returns Result.Success(polishedText)
        coEvery { recordRepository.insert(any()) } returns 1L

        // When
        viewModel.processAudio(audioPath)
        advanceUntilIdle()

        // Then
        assertEquals(polishedText, viewModel.result.value)
    }

    @Test
    fun `processAudio should update error on ASR failure`() = runTest {
        // Given
        val audioPath = "/mock/audio.pcm"
        coEvery { asrRepository.recognize(audioPath) } returns
            Result.Failure(AppError.NetworkError("网络错误"))

        // When
        viewModel.processAudio(audioPath)
        advanceUntilIdle()

        // Then
        assertNotNull(viewModel.error.value)
        assertTrue(viewModel.error.value!!.contains("网络"))
    }

    @Test
    fun `processAudio should show loading state`() = runTest {
        // Given
        val audioPath = "/mock/audio.pcm"
        coEvery { asrRepository.recognize(audioPath) } coAnswers {
            delay(100)
            Result.Success("test")
        }
        coEvery { aiRepository.polish(any(), any()) } returns Result.Success("test")
        coEvery { recordRepository.insert(any()) } returns 1L

        // When
        val job = launch { viewModel.processAudio(audioPath) }
        advanceTimeBy(50)

        // Then
        assertTrue(viewModel.isLoading.value)

        // Cleanup
        advanceUntilIdle()
        job.cancel()
    }
}
```

### 4.2 Repository 测试

```kotlin
// src/test/java/com/aivoice/input/repository/AsrRepositoryTest.kt
class AsrRepositoryTest {

    private lateinit var repository: AsrRepositoryImpl
    private val asrApi: AsrApi = mockk()
    private val context: Context = mockk()

    @BeforeEach
    fun setup() {
        repository = AsrRepositoryImpl(asrApi, context)
    }

    @Test
    fun `recognize should return text on successful API call`() = runTest {
        // Given
        val audioPath = "/mock/audio.pcm"
        val expectedText = "测试文本"
        coEvery { asrApi.recognize(any()) } returns TestConfig.mockAsrResponse(expectedText)

        // When
        val result = repository.recognize(audioPath)

        // Then
        assertTrue(result is Result.Success)
        assertEquals(expectedText, (result as Result.Success).data)
    }

    @Test
    fun `recognize should return error on API failure`() = runTest {
        // Given
        val audioPath = "/mock/audio.pcm"
        coEvery { asrApi.recognize(any()) } returns AsrResponse(
            isSuccess = false,
            text = null,
            code = 1001,
            message = "音频格式错误"
        )

        // When
        val result = repository.recognize(audioPath)

        // Then
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.ServiceError)
    }

    @Test
    fun `recognize should handle network exception`() = runTest {
        // Given
        val audioPath = "/mock/audio.pcm"
        coEvery { asrApi.recognize(any()) } throws IOException("Network error")

        // When
        val result = repository.recognize(audioPath)

        // Then
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is AppError.NetworkError)
    }
}
```

### 4.3 Flow 测试

```kotlin
// src/test/java/com/aivoice/input/viewmodel/HistoryViewModelTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private lateinit var viewModel: HistoryViewModel
    private val recordRepository: RecordRepository = mockk()

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `records Flow should emit data from repository`() = runTest {
        // Given
        val mockRecords = listOf(
            RecordEntity(id = 1, originalText = "测试1", polishedText = "测试1。", style = "FORMAL", duration = 5000),
            RecordEntity(id = 2, originalText = "测试2", polishedText = "测试2。", style = "FORMAL", duration = 3000)
        )
        val mockFlow = flowOf(mockRecords)
        every { recordRepository.getAllRecords() } returns mockFlow

        // When
        viewModel = HistoryViewModel(recordRepository)

        // Then
        viewModel.records.test {
            val emitted = awaitItem()
            assertEquals(2, emitted.size)
            assertEquals("测试1", emitted[0].originalText)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### 4.4 仪器测试 - Room 数据库

```kotlin
// src/androidTest/java/com/aivoice/input/db/RecordDaoTest.kt
@RunWith(AndroidJUnit4::class)
class RecordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var recordDao: RecordDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        recordDao = db.recordDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insert_and_retrieve_record() = runTest {
        // Given
        val record = RecordEntity(
            originalText = "测试文本",
            polishedText = "测试文本。",
            style = "FORMAL",
            duration = 5000
        )

        // When
        val id = recordDao.insert(record)
        val retrieved = recordDao.getById(id)

        // Then
        assertEquals("测试文本", retrieved?.originalText)
        assertEquals("测试文本。", retrieved?.polishedText)
    }

    @Test
    fun delete_old_records() = runTest {
        // Given
        val oldRecord = RecordEntity(
            originalText = "旧记录",
            polishedText = "旧记录。",
            style = "FORMAL",
            duration = 5000,
            createdAt = System.currentTimeMillis() - 31 * 24 * 60 * 60 * 1000L // 31 天前
        )
        val newRecord = RecordEntity(
            originalText = "新记录",
            polishedText = "新记录。",
            style = "FORMAL",
            duration = 5000,
            createdAt = System.currentTimeMillis()
        )

        recordDao.insert(oldRecord)
        recordDao.insert(newRecord)

        // When
        val cutoffTime = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
        recordDao.deleteOldRecords(cutoffTime)

        // Then
        val remaining = recordDao.getAllSync()
        assertEquals(1, remaining.size)
        assertEquals("新记录", remaining[0].originalText)
    }

    @Test
    fun search_records_by_content() = runTest {
        // Given
        recordDao.insert(RecordEntity(originalText = "今天天气不错", polishedText = "今天天气不错。", style = "FORMAL", duration = 5000))
        recordDao.insert(RecordEntity(originalText = "明天去公园", polishedText = "明天去公园。", style = "FORMAL", duration = 3000))
        recordDao.insert(RecordEntity(originalText = "后天看电影", polishedText = "后天看电影。", style = "FORMAL", duration = 4000))

        // When
        val results = recordDao.search("%天气%")

        // Then
        assertEquals(1, results.size)
        assertTrue(results[0].originalText.contains("天气"))
    }
}
```

---

## 5. 测试覆盖范围

### 单元测试覆盖

| 模块 | 测试内容 | 覆盖目标 |
|------|----------|----------|
| ViewModel | 状态变化、业务逻辑、错误处理 | 90% |
| Repository | API 调用、数据转换、错误处理 | 85% |
| Util | 工具函数、格式化、验证 | 95% |
| Model | 数据类、转换逻辑 | 80% |

### 仪器测试覆盖

| 模块 | 测试内容 |
|------|----------|
| Room 数据库 | CRUD 操作、迁移、查询 |
| AccessibilityService | 文字注入、权限检查 |
| UI 交互 | 页面跳转、按钮点击 |

---

## 6. 测试执行脚本

### 6.1 单元测试脚本

```bash
#!/bin/bash
# scripts/run_unit_tests.sh

echo "Running unit tests..."
./gradlew test --stacktrace

# 检查测试结果
if [ $? -eq 0 ]; then
    echo "✅ All unit tests passed!"
else
    echo "❌ Unit tests failed!"
    exit 1
fi
```

### 6.2 仪器测试脚本

```bash
#!/bin/bash
# scripts/run_instrumented_tests.sh

echo "Running instrumented tests..."
./gradlew connectedAndroidTest --stacktrace

# 检查测试结果
if [ $? -eq 0 ]; then
    echo "✅ All instrumented tests passed!"
else
    echo "❌ Instrumented tests failed!"
    exit 1
fi
```

### 6.3 全量测试脚本

```bash
#!/bin/bash
# scripts/run_all_tests.sh

echo "Running all tests..."

# 单元测试
./scripts/run_unit_tests.sh
if [ $? -ne 0 ]; then exit 1; fi

# 仪器测试（需要连接设备）
echo "Please ensure a device is connected for instrumented tests."
read -p "Press Enter to continue..."

./scripts/run_instrumented_tests.sh
if [ $? -ne 0 ]; then exit 1; fi

echo "✅ All tests completed successfully!"
```

---

## 7. 故障排查

### 常见问题

**问题 1：单元测试提示 "Unresolved reference: mockk"**

解决：确保 `build.gradle.kts` 中已添加 mockk 依赖
```kotlin
testImplementation("io.mockk:mockk:1.13.8")
```

**问题 2：仪器测试提示 "No tests found"**

解决：确保测试类使用了正确的注解
```kotlin
@RunWith(AndroidJUnit4::class)
class MyTest { ... }
```

**问题 3：协程测试不稳定**

解决：使用 `runTest` 和 `advanceUntilIdle()` 确保协程执行完成
```kotlin
@Test
fun test() = runTest {
    viewModel.doSomething()
    advanceUntilIdle()  // 等待所有协程完成
    // 断言...
}
```

**问题 4：Room 测试提示 "Cannot access database on the main thread"**

解决：在测试配置中允许主线程查询
```kotlin
Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
    .allowMainThreadQueries()
    .build()
```

---

## 8. CI/CD 集成

### GitHub Actions 配置示例

```yaml
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run Unit Tests
        run: ./gradlew test

      - name: Upload Test Results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: unit-test-results
          path: app/build/reports/tests/

  instrumented-tests:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run Instrumented Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 30
          script: ./gradlew connectedAndroidTest

      - name: Upload Test Results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: instrumented-test-results
          path: app/build/reports/androidTests/
```

---

## 9. 参考

- [JUnit 5 官方文档](https://junit.org/junit5/docs/current/user-guide/)
- [MockK 官方文档](https://mockk.io/)
- [Android Testing 官方指南](https://developer.android.com/training/testing)
- [Kotlin Coroutines 测试指南](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
