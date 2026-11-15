package com.example.ddlreminder.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Serializes the whole database into a portable JSON document so the data can be
 * moved between devices. Only the fields that matter to the user are stored;
 * reminder bookkeeping flags are rebuilt after importing.
 */
object BackupCodec {

    const val FORMAT = "ddl-reminder-backup"
    const val FORMAT_VERSION = 1

    data class BackupTask(
        val title: String,
        val ddl: Long?,
        val isCompleted: Boolean,
        val completedAt: Long?,
        val createdAt: Long,
        val updatedAt: Long,
        val sortOrder: Int?
    )

    data class BackupList(
        val name: String,
        val createdAt: Long,
        val tasks: List<BackupTask>
    )

    data class BackupDocument(
        val userName: String?,
        val lists: List<BackupList>
    ) {
        val taskCount: Int get() = lists.sumOf { it.tasks.size }
    }

    fun encode(userName: String, lists: List<BackupList>, exportedAt: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", exportedAt)
        root.put("userName", userName)

        val listArray = JSONArray()
        lists.forEach { list ->
            val listObject = JSONObject()
            listObject.put("name", list.name)
            listObject.put("createdAt", list.createdAt)

            val taskArray = JSONArray()
            list.tasks.forEach { task ->
                val taskObject = JSONObject()
                taskObject.put("title", task.title)
                putNullable(taskObject, "ddl", task.ddl)
                taskObject.put("isCompleted", task.isCompleted)
                putNullable(taskObject, "completedAt", task.completedAt)
                taskObject.put("createdAt", task.createdAt)
                taskObject.put("updatedAt", task.updatedAt)
                if (task.sortOrder == null) {
                    taskObject.put("sortOrder", JSONObject.NULL)
                } else {
                    taskObject.put("sortOrder", task.sortOrder)
                }
                taskArray.put(taskObject)
            }
            listObject.put("tasks", taskArray)
            listArray.put(listObject)
        }
        root.put("lists", listArray)

        return root.toString(2)
    }

    /**
     * @throws IllegalArgumentException when the payload is not a DDL Reminder backup.
     */
    fun decode(raw: String): BackupDocument {
        val root = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw IllegalArgumentException("文件不是有效的 JSON 备份")
        }

        val format = root.optString("format")
        if (format != FORMAT) {
            throw IllegalArgumentException("不是 DDL Reminder 的备份文件")
        }
        val version = root.optInt("formatVersion", 0)
        if (version <= 0 || version > FORMAT_VERSION) {
            throw IllegalArgumentException("备份文件版本不受支持")
        }

        val listArray = root.optJSONArray("lists")
            ?: throw IllegalArgumentException("备份文件缺少清单数据")

        val lists = ArrayList<BackupList>(listArray.length())
        for (i in 0 until listArray.length()) {
            val listObject = listArray.optJSONObject(i) ?: continue
            val name = listObject.optString("name").trim()
            if (name.isEmpty()) continue

            val createdAt = listObject.optLong("createdAt", System.currentTimeMillis())
            val taskArray = listObject.optJSONArray("tasks")
            val tasks = ArrayList<BackupTask>(taskArray?.length() ?: 0)
            if (taskArray != null) {
                for (j in 0 until taskArray.length()) {
                    val taskObject = taskArray.optJSONObject(j) ?: continue
                    val title = taskObject.optString("title").trim()
                    if (title.isEmpty()) continue
                    tasks.add(
                        BackupTask(
                            title = title,
                            ddl = optNullableLong(taskObject, "ddl"),
                            isCompleted = taskObject.optBoolean("isCompleted", false),
                            completedAt = optNullableLong(taskObject, "completedAt"),
                            createdAt = taskObject.optLong("createdAt", createdAt),
                            updatedAt = taskObject.optLong("updatedAt", createdAt),
                            sortOrder = optNullableInt(taskObject, "sortOrder")
                        )
                    )
                }
            }
            lists.add(BackupList(name = name, createdAt = createdAt, tasks = tasks))
        }

        val userName = root.optString("userName").takeIf { it.isNotBlank() }
        return BackupDocument(userName = userName, lists = lists)
    }

    private fun putNullable(target: JSONObject, key: String, value: Long?) {
        target.put(key, value ?: JSONObject.NULL)
    }

    private fun optNullableLong(source: JSONObject, key: String): Long? =
        if (!source.has(key) || source.isNull(key)) null else source.optLong(key)

    private fun optNullableInt(source: JSONObject, key: String): Int? =
        if (!source.has(key) || source.isNull(key)) null else source.optInt(key)
}
