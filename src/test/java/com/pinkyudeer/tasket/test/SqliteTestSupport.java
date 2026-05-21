package com.pinkyudeer.tasket.test;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.After;
import org.junit.Before;

import com.pinkyudeer.tasket.db.SQLiteManager;
import com.pinkyudeer.tasket.task.TaskSqlHelper;

public abstract class SqliteTestSupport {

    @Before
    public void openDatabase() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SQLiteManager.setConnectionForTesting(connection);
        SQLiteManager.isWorldLoaded = false;
        TaskSqlHelper.initTaskDataBase();
    }

    @After
    public void closeDatabase() {
        SQLiteManager.close();
    }

}
