# `:local` Module — RoomDB + Project JSON Persistence Plan

## 1. Module Architecture

### Dependency graph

```
 :app ────► :local
  │            │
  │            Room only (ProjectEntity, ProjectDao, AppDatabase)
  │            NO app model imports. Pure DB layer.
  │
  ├── JSON serialization (Project ↔ kotlinx.serialization)
  ├── JSON file I/O   (ProjectJsonManager)
  └── Repository      (ProjectRepository — bridges Room + JSON + models)
```

### Rule: no circular dependencies

| Code | Lives in | Sees |
|------|----------|------|
| `ProjectEntity` | `:local` | Nothing from `:app` |
| `ProjectDao` | `:local` | Only `ProjectEntity` |
| `AppDatabase` | `:local` | Only `ProjectDao`, `ProjectEntity` |
| `ProjectJsonSerializer` | `:app` | All `:app` model classes (`Project`, `Clip`, `AppliedEffect`, etc.) |
| `ProjectJsonManager` | `:app` | `Context` + file system, raw JSON strings |
| `ProjectRepository` | `:app` | `ProjectDao` (from `:local`) + `ProjectJsonSerializer` + `ProjectJsonManager` |

### Why Converters stay in `:local`

The Room entity stores `aspect_ratio` as `TEXT` (plain `String`), no TypeConverter.
Converting between `String` and `AspectRatio` enum happens in `ProjectRepository` inside `:app`,
where the enum is accessible.

### `:app` must depend on `:local`

```kotlin
// app/build.gradle.kts — ADD this dependency
dependencies {
    implementation(project(":local"))
    // ... existing deps ...
}
```

---

## 2. Room Schema — `ProjectEntity`

### Table: `projects`

| Column | Kotlin type | SQL type | Constraints | Description |
|--------|------------|----------|-------------|-------------|
| `id` | `String` | TEXT | PRIMARY KEY | UUID |
| `name` | `String` | TEXT | NOT NULL | User-visible project name |
| `thumbnail_path` | `String?` | TEXT | NULLABLE | Absolute path to thumbnail bitmap |
| `duration_us` | `Long` | INTEGER | NOT NULL DEFAULT 0 | Total project duration in microseconds |
| `aspect_ratio` | `String` | TEXT | NOT NULL | Enum `.name`: `"SixteenNine"`, `"FourThree"`, `"OneOne"`, `"NineSixteen"` |
| `date_created_ms` | `Long` | INTEGER | NOT NULL | Epoch millis |
| `date_modified_ms` | `Long` | INTEGER | NOT NULL | Epoch millis |
| `project_json_path` | `String` | TEXT | NOT NULL | Path to JSON file: `{filesDir}/projects/{id}.json` |

```kotlin
// local/.../db/entity/ProjectEntity.kt

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val thumbnailPath: String?,
    val durationUs: Long,
    val aspectRatio: String,       // NOT an enum — plain string, no TypeConverter
    val dateCreatedMs: Long,
    val dateModifiedMs: Long,
    val projectJsonPath: String
)
```

### DAO — `ProjectDao`

```kotlin
// local/.../dao/ProjectDao.kt

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY date_modified_ms DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("UPDATE projects SET date_modified_ms = :timestamp WHERE id = :projectId")
    suspend fun touchModifiedDate(projectId: String, timestamp: Long)

    @Query("UPDATE projects SET name = :name, date_modified_ms = :now WHERE id = :projectId")
    suspend fun renameProject(projectId: String, name: String, now: Long)

    @Query("UPDATE projects SET thumbnail_path = :path, date_modified_ms = :now WHERE id = :projectId")
    suspend fun updateThumbnailPath(projectId: String, path: String?, now: Long)

    @Query("UPDATE projects SET duration_us = :durationUs, aspect_ratio = :aspectRatio, date_modified_ms = :now WHERE id = :projectId")
    suspend fun updateCatalogFields(projectId: String, durationUs: Long, aspectRatio: String, now: Long)
}
```

Key fixes from previous version:
- No default params (Room struggles with Kotlin default parameter stub generation).
- `aspectRatio` is `String` — no TypeConverter needed in `:local`.
- `updateCatalogFields` atomically patches the three fields that change on save.

### Database — `AppDatabase`

```kotlin
// local/.../db/AppDatabase.kt

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
```

Note: no singleton `getInstance()` here. The singleton is managed by Hilt (see section 5).

---

## 3. JSON File Schema — Full Editing Data Per Project

### File location

```
{context.filesDir}/projects/{projectId}.json
```

Actual path on device:
```
/data/data/id.my.daniza.reelcraft/files/projects/proj_a1b2c3d4.json
```

This path is stored in Room as `project_json_path`.

### Complete JSON schema

Every field of `Project` (and all nested data classes) is serialized. The file is self-contained
so it can be exported and re-imported independently of Room.

```jsonc
{
  "schemaVersion": 1,

  // ── Project identity & catalog fields (all from Project data class) ──
  "id": "proj_a1b2c3d4",
  "name": "Summer Trip",
  "thumbnailPath": "/data/data/.../thumbnails/proj_a1b2c3d4_thumb.jpg",
  "durationUs": 45000000,
  "dateCreatedMs": 1718000000000,
  "dateModifiedMs": 1718100000000,

  // ── Canvas ──
  "aspectRatio": "SixteenNine",
  "volume": 1.0,

  // ── Clips ──
  "clips": [
    {
      "id": "clip_1",
      "sourcePath": "/storage/emulated/0/DCIM/Camera/VID_20250601.mp4",
      "trimStartUs": 0,
      "trimEndUs": 15000000,
      "speed": 1.0,
      "volume": 1.0,
      "orderIndex": 0,

      // Per-clip effects
      "effects": [
        {
          "id": "effect_1",
          "presetId": "clarendon",
          "maskType": "WholeFrame",
          "intensity": 0.8,
          "enabled": true,
          "keyframes": [
            {
              "positionUs": 0,
              "intensity": 0.5,
              "interpolation": "EaseInOut"
            },
            {
              "positionUs": 7500000,
              "intensity": 1.0,
              "interpolation": "EaseInOut"
            }
          ],
          "params": null
        }
      ],

      // Text overlays
      "textOverlays": [
        {
          "id": "txt_1",
          "text": "Hello World",
          "fontName": "Default",
          "fontSize": 36,
          "colorArgb": 4294967295,
          "positionX": 0.5,
          "positionY": 0.5,
          "rotationDeg": 0.0,
          "startOffsetUs": 0,
          "endOffsetUs": 5000000
        }
      ],

      // Transition to next clip
      "transitionOut": {
        "type": "Crossfade",
        "durationUs": 500000
      }
    }
  ],

  // ── Background Music ──
  "musicTrack": {
    "sourcePath": "/storage/emulated/0/Music/summer_vibes.mp3",
    "name": "Summer Vibes",
    "volume": 0.5,
    "trimStartUs": 0,
    "trimEndUs": 0
  },

  // ── Session state (timeline UI resume) ──
  // Not part of Project data class — deserialized separately.
  "timelineState": {
    "currentPositionUs": 5000000,
    "isPlaying": false,
    "durationUs": 45000000,
    "zoomLevel": 1.0,
    "selectedClipId": "clip_1",
    "selectedEffectId": null
  }
}
```

### Field coverage audit — every model field vs JSON

#### `Project` (all 10 constructor fields)

| Field | JSON key | Type in JSON | Match |
|-------|----------|-------------|-------|
| `id: String` | `"id"` | string | ✅ |
| `name: String` | `"name"` | string | ✅ |
| `thumbnailPath: String?` | `"thumbnailPath"` | string \| null | ✅ (was MISSING before) |
| `durationUs: Long` | `"durationUs"` | number | ✅ (was MISSING before) |
| `dateCreatedMs: Long` | `"dateCreatedMs"` | number | ✅ (was MISSING before) |
| `dateModifiedMs: Long` | `"dateModifiedMs"` | number | ✅ (was MISSING before) |
| `clips: List<Clip>` | `"clips"` | array | ✅ |
| `musicTrack: MusicTrack?` | `"musicTrack"` | object \| null | ✅ |
| `aspectRatio: AspectRatio` | `"aspectRatio"` | string (enum name) | ✅ |
| `volume: Float` | `"volume"` | number | ✅ |

> `effectiveDurationUs` is a computed property (getter), not serialized — correct.

#### `Clip` (all 10 constructor fields)

| Field | JSON key | Type | Match |
|-------|----------|------|-------|
| `id: String` | `"id"` | string | ✅ |
| `sourcePath: String` | `"sourcePath"` | string | ✅ |
| `trimStartUs: Long` | `"trimStartUs"` | number | ✅ |
| `trimEndUs: Long` | `"trimEndUs"` | number | ✅ |
| `speed: Float` | `"speed"` | number | ✅ |
| `volume: Float` | `"volume"` | number | ✅ |
| `effects: List<AppliedEffect>` | `"effects"` | array | ✅ |
| `textOverlays: List<TextOverlay>` | `"textOverlays"` | array | ✅ |
| `orderIndex: Int` | `"orderIndex"` | number | ✅ |
| `transitionOut: Transition?` | `"transitionOut"` | object \| null | ✅ |

> `durationUs` and `effectiveDurationUs` are computed properties, not serialized — correct.

#### `AppliedEffect` (all 7 constructor fields)

| Field | JSON key | Type | Match |
|-------|----------|------|-------|
| `id: String` | `"id"` | string | ✅ |
| `presetId: String` | `"presetId"` | string | ✅ |
| `maskType: MaskType` | `"maskType"` | string (enum name) | ✅ |
| `intensity: Float` | `"intensity"` | number | ✅ |
| `enabled: Boolean` | `"enabled"` | boolean | ✅ |
| `keyframes: List<Keyframe>` | `"keyframes"` | array | ✅ |
| `params: FloatArray?` | `"params"` | array \| null | ✅ (needs custom serializer) |

#### `Keyframe` (all 3 constructor fields)

| Field | JSON key | Type | Match |
|-------|----------|------|-------|
| `positionUs: Long` | `"positionUs"` | number | ✅ |
| `intensity: Float` | `"intensity"` | number | ✅ |
| `interpolation: Interpolation` | `"interpolation"` | string (enum name) | ✅ |

#### `TextOverlay` (all 10 constructor fields)

| Field | JSON key | Type | Match |
|-------|----------|------|-------|
| `id: String` | `"id"` | string | ✅ |
| `text: String` | `"text"` | string | ✅ |
| `fontName: String` | `"fontName"` | string | ✅ |
| `fontSize: Int` | `"fontSize"` | number | ✅ |
| `colorArgb: Long` | `"colorArgb"` | number | ✅ |
| `positionX: Float` | `"positionX"` | number | ✅ |
| `positionY: Float` | `"positionY"` | number | ✅ |
| `rotationDeg: Float` | `"rotationDeg"` | number | ✅ |
| `startOffsetUs: Long` | `"startOffsetUs"` | number | ✅ |
| `endOffsetUs: Long` | `"endOffsetUs"` | number | ✅ |

#### `Transition` (all 2 constructor fields)

| Field | JSON key | Type | Match |
|-------|----------|------|-------|
| `type: TransitionType` | `"type"` | string (enum name) | ✅ |
| `durationUs: Long` | `"durationUs"` | number | ✅ |

#### `MusicTrack` (all 5 constructor fields)

| Field | JSON key | Type | Match |
|-------|----------|------|-------|
| `sourcePath: String` | `"sourcePath"` | string | ✅ |
| `name: String` | `"name"` | string | ✅ |
| `volume: Float` | `"volume"` | number | ✅ |
| `trimStartUs: Long` | `"trimStartUs"` | number | ✅ |
| `trimEndUs: Long` | `"trimEndUs"` | number | ✅ |

#### `TimelineState` (all 6 constructor fields)

| Field | JSON key | Type | Match |
|-------|----------|------|-------|
| `currentPositionUs: Long` | `"currentPositionUs"` | number | ✅ |
| `isPlaying: Boolean` | `"isPlaying"` | boolean | ✅ |
| `durationUs: Long` | `"durationUs"` | number | ✅ |
| `zoomLevel: Float` | `"zoomLevel"` | number | ✅ |
| `selectedClipId: String?` | `"selectedClipId"` | string \| null | ✅ |
| `selectedEffectId: String?` | `"selectedEffectId"` | string \| null | ✅ |

### Enum serialization strategy

All enums serialized via their `.name` property (eg. `AspectRatio.SixteenNine` → `"SixteenNine"`).
No custom serializers needed for enums — kotlinx.serialization supports this via `@Serializable` on the enum class.

| Enum class | Values |
|------------|--------|
| `AspectRatio` | `SixteenNine`, `FourThree`, `OneOne`, `NineSixteen` |
| `MaskType` | `WholeFrame`, `Foreground`, `Background` |
| `Interpolation` | `Linear`, `EaseIn`, `EaseOut`, `EaseInOut` |
| `TransitionType` | `None`, `Crossfade`, `WipeLeft`, `WipeRight`, `WipeUp`, `WipeDown` |

### `FloatArray` serialization

`AppliedEffect.params: FloatArray?` is NOT natively handled by kotlinx.serialization. Two approaches:

**Option A (recommended):** Change the model from `FloatArray?` to `List<Float>?`. This works
out of the box with kotlinx.serialization. Update the few places that read/write `params`
(EditorViewModel.kt line 273-274, PresetEngine).

**Option B:** Register a custom `KSerializer<FloatArray?>` that serializes as a JSON number array
and deserializes back with `floatArrayOf()`. Requires annotation `@Serializable(with = FloatArraySerializer::class)`
on the field.

For the plan, option A is simpler and recommended since FloatArray provides no special benefit
here (both are heap-allocated, params is small, no native interop on this field).

### `schemaVersion` field

Not part of any model class — injected by the serializer at write time, stripped at read time.
Allows future JSON format changes to be versioned. Current version: `1`.

---

## 4. Repository — Unified API for :app

Located in `:app` (not `:local`) because it bridges Room entities with app model classes.

```kotlin
// app/.../data/repository/ProjectRepository.kt

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val jsonSerializer: ProjectJsonSerializer,
    private val jsonManager: ProjectJsonManager
) {
    // ── Catalog (Room-backed, for Home screen) ──────────────────────

    /** Flow of project summaries ordered by last modified */
    fun observeProjects(): Flow<List<ProjectEntity>> =
        projectDao.getAllProjects()

    /** Create a new project — writes empty JSON + inserts Room entry */
    suspend fun createProject(name: String, aspectRatio: AspectRatio): String {
        val projectId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val jsonPath = jsonManager.projectFilePath(projectId)

        val project = Project(
            id = projectId,
            name = name,
            aspectRatio = aspectRatio,
            dateCreatedMs = now,
            dateModifiedMs = now
        )
        jsonManager.saveProject(project, jsonSerializer)

        val entity = project.toEntity(jsonPath, now)
        projectDao.upsertProject(entity)
        return projectId
    }

    /** Delete project (Room + JSON file + thumbnail) */
    suspend fun deleteProject(projectId: String) {
        val entity = projectDao.getProjectById(projectId)
        projectDao.deleteProjectById(projectId)
        jsonManager.deleteProjectFile(projectId)
        entity?.thumbnailPath?.let { thumbnailFile(it).delete() }
    }

    /** Rename a project */
    suspend fun renameProject(projectId: String, newName: String) {
        val now = System.currentTimeMillis()
        projectDao.renameProject(projectId, newName, now)
        // Also update JSON so file stays consistent
        val entity = projectDao.getProjectById(projectId) ?: return
        val project = jsonManager.loadProject(entity.projectJsonPath, jsonSerializer) ?: return
        jsonManager.saveProject(project.copy(name = newName, dateModifiedMs = now), jsonSerializer)
    }

    // ── Full editing data (JSON-backed) ─────────────────────────────

    /** Load full project including clips/effects/text/music (for EditorViewModel) */
    suspend fun loadFullProject(projectId: String): LoadResult? {
        val entity = projectDao.getProjectById(projectId) ?: return null
        val result = jsonManager.loadProjectWithTimeline(entity.projectJsonPath, jsonSerializer) ?: return null
        return result
    }

    /** Save full project editing data (called after edits) */
    suspend fun saveFullProject(project: Project, timelineState: TimelineState? = null) {
        val now = System.currentTimeMillis()
        val jsonPath = jsonManager.projectFilePath(project.id)

        val saved = project.copy(dateModifiedMs = now)
        jsonManager.saveProject(saved, timelineState, jsonSerializer)

        val entity = saved.toEntity(jsonPath, now)
        projectDao.upsertProject(entity)
    }

    // ── Export / Import ─────────────────────────────────────────────

    /** Export as standalone JSON file to cache dir (share/backup) */
    fun exportProjectJson(projectId: String): File? {
        val entity = runBlocking { projectDao.getProjectById(projectId) } ?: return null
        val source = File(entity.projectJsonPath)
        if (!source.exists()) return null
        val exportDir = File(context.cacheDir, "exports")
        exportDir.mkdirs()
        val dest = File(exportDir, source.name)
        source.copyTo(dest, overwrite = true)
        return dest
    }

    /** Import from a JSON file — creates Room entry + copies JSON to projects dir */
    suspend fun importProjectFromJson(jsonFile: File): String {
        val project = jsonSerializer.deserialize(jsonFile.readText())
        val now = System.currentTimeMillis()
        val imported = project.copy(
            id = UUID.randomUUID().toString(),
            dateCreatedMs = now,
            dateModifiedMs = now
        )
        val jsonPath = jsonManager.saveProject(imported, null, jsonSerializer)
        val entity = imported.toEntity(jsonPath, now)
        projectDao.upsertProject(entity)
        return imported.id
    }

    private fun thumbnailFile(path: String): File = File(path)

    companion object {
        data class LoadResult(
            val project: Project,
            val timelineState: TimelineState?
        )
    }
}

// Extension: Project → ProjectEntity mapping
private fun Project.toEntity(jsonPath: String, now: Long) = ProjectEntity(
    id = id,
    name = name,
    thumbnailPath = thumbnailPath,
    durationUs = effectiveDurationUs,
    aspectRatio = aspectRatio.name,
    dateCreatedMs = dateCreatedMs,
    dateModifiedMs = now,
    projectJsonPath = jsonPath
)
```

---

## 5. JSON Classes (in `:app`)

### ProjectJsonSerializer

```kotlin
// app/.../data/json/ProjectJsonSerializer.kt

@Singleton
class ProjectJsonSerializer @Inject constructor() {

    fun serialize(project: Project, timelineState: TimelineState?): String {
        val dto = ProjectJsonDto(
            schemaVersion = 1,
            id = project.id,
            name = project.name,
            thumbnailPath = project.thumbnailPath,
            durationUs = project.durationUs,
            dateCreatedMs = project.dateCreatedMs,
            dateModifiedMs = project.dateModifiedMs,
            aspectRatio = project.aspectRatio.name,
            volume = project.volume,
            clips = project.clips.map { it.toDto() },
            musicTrack = project.musicTrack?.toDto(),
            timelineState = timelineState?.toDto()
        )
        return json.encodeToString(ProjectJsonDto.serializer(), dto)
    }

    fun deserialize(jsonString: String): DeserializeResult {
        val dto = json.decodeFromString(ProjectJsonDto.serializer(), jsonString)
        return DeserializeResult(
            project = dto.toProject(),
            timelineState = dto.timelineState?.toTimelineState()
        )
    }

    data class DeserializeResult(
        val project: Project,
        val timelineState: TimelineState?
    )
}
```

The serializer uses kotlinx.serialization `@Serializable` DTO classes that mirror the JSON schema.
These DTOs are internal to the serializer — not exposed outside this layer.

### ProjectJsonManager

```kotlin
// app/.../data/json/ProjectJsonManager.kt

@Singleton
class ProjectJsonManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val projectsDir: File
        get() = File(context.filesDir, "projects").also { it.mkdirs() }

    fun projectFilePath(projectId: String): String =
        File(projectsDir, "$projectId.json").absolutePath

    /** Writes project + optional timelineState to JSON file. Returns path. */
    fun saveProject(
        project: Project,
        timelineState: TimelineState? = null,
        serializer: ProjectJsonSerializer
    ): String {
        val path = projectFilePath(project.id)
        val tmpFile = File("$path.tmp")
        val json = serializer.serialize(project, timelineState)
        tmpFile.writeText(json)
        tmpFile.renameTo(File(path))        // atomic rename
        return path
    }

    /** Reads Project + TimelineState from JSON file */
    fun loadProjectWithTimeline(
        jsonPath: String,
        serializer: ProjectJsonSerializer
    ): ProjectRepository.LoadResult? {
        val file = File(jsonPath)
        if (!file.exists()) return null
        val json = file.readText()
        val result = serializer.deserialize(json)
        return ProjectRepository.LoadResult(result.project, result.timelineState)
    }

    fun loadProject(jsonPath: String, serializer: ProjectJsonSerializer): Project? {
        val file = File(jsonPath)
        if (!file.exists()) return null
        return serializer.deserialize(file.readText()).project
    }

    fun deleteProjectFile(projectId: String): Boolean {
        val file = File(projectFilePath(projectId))
        return if (file.exists()) file.delete() else true
    }

    fun projectFileExists(projectId: String): Boolean =
        File(projectFilePath(projectId)).exists()
}
```

---

## 6. Hilt DI Wiring

### `:local` module — no DI module needed

Room database is provided via a Hilt module in `:app` that constructs the database
with the application context. No `@Module` inside `:local`.

### `:app` module — new DI module

```kotlin
// app/.../di/LocalModule.kt  (NEW FILE)

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "reelcraft.db"
    ).build()

    @Provides
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()
}
```

### Updated `AppModule.kt` (existing file — add repository)

```kotlin
// Add ProjectRepository to the existing AppModule or LocalModule:
@Provides
@Singleton
fun provideProjectRepository(
    projectDao: ProjectDao,
    jsonSerializer: ProjectJsonSerializer,
    jsonManager: ProjectJsonManager
): ProjectRepository = ProjectRepository(projectDao, jsonSerializer, jsonManager)
```

---

## 7. File Structure After Implementation

```
local/
├── build.gradle.kts
├── consumer-rules.keep
└── src/
    ├── androidTest/java/id/my/daniza/local/
    │   └── ProjectDaoTest.kt
    ├── main/
    │   ├── AndroidManifest.xml
    │   └── java/id/my/daniza/local/
    │       ├── db/
    │       │   ├── AppDatabase.kt              ← Room singleton
    │       │   └── entity/
    │       │       └── ProjectEntity.kt        ← Room entity (aspectRatio: String)
    │       └── dao/
    │           └── ProjectDao.kt               ← Room DAO
    └── test/java/id/my/daniza/local/
        └── ExampleUnitTest.kt                  (existing, keep)

app/src/main/java/id/my/daniza/reelcraft/
├── data/
│   ├── DummyProjects.kt                        (existing, KEEP during migration)
│   ├── json/
│   │   ├── ProjectJsonSerializer.kt           ← NEW: Project ↔ JSON
│   │   ├── ProjectJsonDtos.kt                 ← NEW: @Serializable DTOs
│   │   └── ProjectJsonManager.kt              ← NEW: file I/O
│   └── repository/
│       └── ProjectRepository.kt               ← NEW: bridges Room + JSON + models
├── di/
│   ├── AppModule.kt                            (existing, EXTEND)
│   └── LocalModule.kt                         ← NEW: provides AppDatabase, ProjectDao
├── model/                                      (existing, UNCHANGED)
│   └── ...
└── viewmodel/
    ├── HomeViewModel.kt                        (existing, UPDATE to use Repository)
    └── EditorViewModel.kt                      (existing, UPDATE to use Repository)
```

---

## 8. Dependencies

### `gradle/libs.versions.toml`

```toml
[versions]
# ... existing versions ...
room = "2.7.1"                          # ADD
kotlinxSerialization = "1.9.0"          # ADD (compatible with Kotlin 2.1.0)

[libraries]
# ... existing libraries ...

# ── Room ──
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# ── Kotlinx Serialization ──
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

[plugins]
# ... existing plugins ...

# ── ADD ──
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

### `local/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)           // ADD
    alias(libs.plugins.ksp)                       // ADD (for Room compiler)
}

android {
    namespace = "id.my.daniza.local"
    compileSdk = 37                              // FIX: remove { version = release(37) }

    defaultConfig {
        minSdk = 27
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"                          // ADD
    }
}

dependencies {
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

### `root build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false   // ADD
}
```

### `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)   // ADD
}

// ... android block unchanged ...

dependencies {
    implementation(project(":local"))            // ADD
    implementation(project(":ffmpeg"))
    implementation(project(":segment"))

    // ... existing deps ...
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Kotlinx Serialization (ADD)
    implementation(libs.kotlinx.serialization.json)

    // ... rest unchanged ...
}
```

---

## 9. ViewModel Updates

### HomeViewModel changes

```kotlin
// Before:
class HomeViewModel : ViewModel() {
    private val _projects = mutableStateListOf<Project>()
    private fun loadProjects() {
        _projects.addAll(DummyProjects.projects)
    }
}

// After:
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ProjectRepository
) : ViewModel() {
    val projects: StateFlow<List<ProjectEntity>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createProject(name: String, aspectRatio: AspectRatio) {
        viewModelScope.launch {
            repository.createProject(name, aspectRatio)
            // Flow auto-updates
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }
}
```

### EditorViewModel changes

```kotlin
// Before:
fun loadProject(projectId: String) {
    val found = DummyProjects.projectById(projectId)
    if (found != null) { project = found }
}

fun saveState() {
    // only in-memory undo stack
}

// After:
fun loadProject(projectId: String) {
    viewModelScope.launch {
        val result = repository.loadFullProject(projectId) ?: return@launch
        project = result.project
        result.timelineState?.let { timelineState = it }
        // ... open decoder etc.
    }
}

fun saveProject() {
    viewModelScope.launch {
        project?.let { repository.saveFullProject(it, timelineState) }
    }
}
```

---

## 10. Data Flow Scenarios

### A. App startup → Home screen

```
HomeViewModel.init()
  └─► repository.observeProjects()
       └─► Room: getAllProjects() → Flow<List<ProjectEntity>>
            Compose observes Flow → renders project cards from entity fields
            (name, thumbnailPath, durationUs, dateModifiedMs — all in Room)
```

### B. Open project → Editor screen

```
EditorViewModel.loadProject(id)
  └─► repository.loadFullProject(id)
       ├─► Room: getProjectById(id) → ProjectEntity (has projectJsonPath)
       └─► JSON: read file at projectJsonPath → deserialize
            Returns LoadResult(project=Project, timelineState=TimelineState?)
            EditorViewModel sets both project and timelineState
```

### C. Edit → Save

```
EditorViewModel.saveProject()
  └─► repository.saveFullProject(project, timelineState)
       ├─► JSON: atomic write to .tmp → rename to {id}.json
       └─► Room: upsertProject(ProjectEntity) with updated dateModifiedMs etc.
            Flow emits new list → Home screen cards auto-update
```

### D. Create new project

```
HomeViewModel.createProject(name, aspectRatio)
  └─► repository.createProject(name, aspectRatio)
       ├─► Generate UUID
       ├─► Create Project object (empty clips, no music)
       ├─► JSON: serialize + write to {id}.json
       ├─► Room: insert ProjectEntity
       └─► Return projectId → navigate to editor
```

### E. Delete project

```
HomeViewModel.deleteProject(id)
  └─► repository.deleteProject(id)
       ├─► Room: delete entity by id
       ├─► JSON: delete {id}.json file
       ├─► Thumbnail: delete thumbnail file if path exists
       └─► Room Flow emits new list → Home screen updates
```

---

## 11. Implementation Order (13 steps)

| Step | What | Files |
|------|------|-------|
| **1** | Add Room + kotlinx.serialization to `libs.versions.toml` | `gradle/libs.versions.toml` |
| **2** | Update `local/build.gradle.kts` — fix compileSdk syntax, add plugins (kotlin-android, ksp), add Room deps | `local/build.gradle.kts` |
| **3** | Update root `build.gradle.kts` — add serialization plugin | `build.gradle.kts` |
| **4** | Update `app/build.gradle.kts` — add serialization plugin, add `implementation(project(":local"))`, add kotlinx-serialization-json | `app/build.gradle.kts` |
| **5** | Create `ProjectEntity` (aspectRatio as String, no TypeConverter) | `local/.../db/entity/ProjectEntity.kt` |
| **6** | Create `ProjectDao` (no default params) | `local/.../dao/ProjectDao.kt` |
| **7** | Create `AppDatabase` | `local/.../db/AppDatabase.kt` |
| **8** | Create `ProjectJsonDtos` (@Serializable classes mirroring JSON schema) | `app/.../data/json/ProjectJsonDtos.kt` |
| **9** | Create `ProjectJsonSerializer` (DTO ↔ Model mapping) | `app/.../data/json/ProjectJsonSerializer.kt` |
| **10** | Create `ProjectJsonManager` (file I/O with atomic writes) | `app/.../data/json/ProjectJsonManager.kt` |
| **11** | Create `ProjectRepository` | `app/.../data/repository/ProjectRepository.kt` |
| **12** | Create `LocalModule` Hilt DI (provides AppDatabase, ProjectDao, serializer, manager, repository) | `app/.../di/LocalModule.kt` |
| **13** | Update `HomeViewModel` + `EditorViewModel` to use Repository | `app/.../viewmodel/HomeViewModel.kt`, `EditorViewModel.kt` |

---

## 12. Edge Cases & Design Decisions

| Concern | Decision |
|---------|----------|
| **Module boundary** | `:local` = Room only (no app model imports). `:app` = JSON + repository (bridges both sides). |
| **AspectRatio in Room** | Stored as `String` (enum name). No TypeConverter needed. Mapped to/from `AspectRatio` enum in `ProjectRepository` inside `:app`. |
| **FloatArray → List<Float>** | Convert `AppliedEffect.params` from `FloatArray?` to `List<Float>?` for clean kotlinx.serialization. Updates `EditorViewModel` and `PresetEngine` accordingly. |
| **TimelineState** | Stored inside project JSON file. Not part of `Project` data class. `ProjectRepository.LoadResult` returns `(project: Project, timelineState: TimelineState?)` together. |
| **Concurrent edits** | Auto-save debounced 500ms after last edit via `viewModelScope.launch { delay(500); saveProject() }`. |
| **File corruption** | Atomic write: serialize → `.tmp` file → `File.renameTo()` (fs atomic on same volume). |
| **Missing JSON file** | Room entry exists but JSON file deleted → `ProjectRepository` deletes the orphan Room entry and returns `null`. |
| **Thumbnail cleanup** | On project delete, also delete `entity.thumbnailPath` file. |
| **JSON migration** | `schemaVersion` field at top of JSON. Future versions add migration in serializer's deserialize path. |
| **Threading** | Room `Flow` dispatches on Room's internal coroutine context. `suspend` DAO methods run on `Dispatchers.IO`. JSON file I/O wrapped in `withContext(Dispatchers.IO)`. |
| **Room database name** | `"reelcraft.db"` stored in app internal storage (`databases/reelcraft.db`). |
