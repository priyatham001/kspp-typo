package com.example.storage

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ScriptManager(context: Context) {

    private val db = ScriptDatabase.getDatabase(context)
    private val dao = db.scriptDao()

    val scripts: Flow<List<ScriptEntity>> = dao.getAllScripts()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            if (dao.getCount() == 0) {
                seedInitialScripts()
            }
        }
    }

    suspend fun saveScript(title: String, content: String, language: String = "text"): Long {
        val script = ScriptEntity(
            title = title,
            content = content,
            language = language,
            updatedAt = System.currentTimeMillis()
        )
        return dao.insertScript(script)
    }

    suspend fun updateScript(id: Long, title: String, content: String, language: String = "text") {
        val script = ScriptEntity(
            id = id,
            title = title,
            content = content,
            language = language,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateScript(script)
    }

    suspend fun renameScript(id: Long, newTitle: String) {
        val existing = dao.getScriptById(id)
        if (existing != null) {
            dao.updateScript(existing.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun duplicateScript(id: Long): Long? {
        val existing = dao.getScriptById(id) ?: return null
        val copy = ScriptEntity(
            title = "${existing.title} (Copy)",
            content = existing.content,
            language = existing.language,
            updatedAt = System.currentTimeMillis()
        )
        return dao.insertScript(copy)
    }

    suspend fun deleteScript(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun getScript(id: Long): ScriptEntity? {
        return dao.getScriptById(id)
    }

    private suspend fun seedInitialScripts() {
        val initialScripts = listOf(
            ScriptEntity(
                title = "greeting.txt",
                language = "text",
                content = "hey folks"
            ),
            ScriptEntity(
                title = "email_draft.txt",
                language = "text",
                content = """Dear Sir,

I would like to submit my lab assignment. Here is my test program:

public class Main {
    public static void main(String[] args) {
        System.out.println("hey folks");
    }
}

Thank you,
PSK BT Auto User"""
            ),
            ScriptEntity(
                title = "commands.txt",
                language = "shell",
                content = """git status
cd project
npm install
python3 app.py"""
            ),
            ScriptEntity(
                title = "Main.java",
                language = "java",
                content = """public class Main {
    public static void main(String[] args) {
        int a = 10;
        int b = 20;

        int sum = a + b;

        System.out.println("Sum = " + sum);
    }
}"""
            ),
            ScriptEntity(
                title = "BinarySearch.java",
                language = "java",
                content = """public class BinarySearch {
    public static int search(int[] arr, int target) {
        int low = 0;
        int high = arr.length - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (arr[mid] == target) return mid;
            if (arr[mid] < target) low = mid + 1;
            else high = mid - 1;
        }
        return -1;
    }

    public static void main(String[] args) {
        int[] numbers = {2, 4, 6, 8, 10, 12, 14};
        int result = search(numbers, 10);
        System.out.println("Index: " + result);
    }
}"""
            )
        )

        for (script in initialScripts) {
            dao.insertScript(script)
        }
    }
}
