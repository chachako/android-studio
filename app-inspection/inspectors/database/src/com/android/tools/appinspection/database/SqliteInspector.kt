/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.appinspection.database

import android.annotation.SuppressLint
import android.app.Application
import android.database.Cursor
import android.database.CursorWrapper
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteClosable
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteStatement
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.inspection.ArtTooling
import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import androidx.sqlite.inspection.SqliteInspectorProtocol.AcquireDatabaseLockCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.AcquireDatabaseLockResponse
import androidx.sqlite.inspection.SqliteInspectorProtocol.CellValue
import androidx.sqlite.inspection.SqliteInspectorProtocol.Column
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command.OneOfCase.ACQUIRE_DATABASE_LOCK
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command.OneOfCase.GET_SCHEMA
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command.OneOfCase.KEEP_DATABASES_OPEN
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command.OneOfCase.QUERY
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command.OneOfCase.RELEASE_DATABASE_LOCK
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command.OneOfCase.TRACK_DATABASES
import androidx.sqlite.inspection.SqliteInspectorProtocol.DatabaseClosedEvent
import androidx.sqlite.inspection.SqliteInspectorProtocol.DatabaseOpenedEvent
import androidx.sqlite.inspection.SqliteInspectorProtocol.DatabasePossiblyChangedEvent
import androidx.sqlite.inspection.SqliteInspectorProtocol.ErrorContent
import androidx.sqlite.inspection.SqliteInspectorProtocol.ErrorContent.ErrorCode
import androidx.sqlite.inspection.SqliteInspectorProtocol.ErrorContent.ErrorCode.ERROR_UNKNOWN
import androidx.sqlite.inspection.SqliteInspectorProtocol.ErrorContent.ErrorCode.ERROR_UNRECOGNISED_COMMAND
import androidx.sqlite.inspection.SqliteInspectorProtocol.ErrorOccurredEvent
import androidx.sqlite.inspection.SqliteInspectorProtocol.ErrorOccurredResponse
import androidx.sqlite.inspection.SqliteInspectorProtocol.ErrorRecoverability
import androidx.sqlite.inspection.SqliteInspectorProtocol.Event
import androidx.sqlite.inspection.SqliteInspectorProtocol.GetSchemaCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.GetSchemaResponse
import androidx.sqlite.inspection.SqliteInspectorProtocol.KeepDatabasesOpenCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.KeepDatabasesOpenResponse
import androidx.sqlite.inspection.SqliteInspectorProtocol.QueryCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.QueryParameterValue
import androidx.sqlite.inspection.SqliteInspectorProtocol.QueryResponse
import androidx.sqlite.inspection.SqliteInspectorProtocol.ReleaseDatabaseLockCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.ReleaseDatabaseLockResponse
import androidx.sqlite.inspection.SqliteInspectorProtocol.Response
import androidx.sqlite.inspection.SqliteInspectorProtocol.Row
import androidx.sqlite.inspection.SqliteInspectorProtocol.Table
import androidx.sqlite.inspection.SqliteInspectorProtocol.TrackDatabasesResponse
import com.android.tools.appinspection.database.RequestCollapsingThrottler.DeferredExecutor
import com.android.tools.appinspection.database.SqliteInspectionExecutors.submit
import com.android.tools.idea.protobuf.ByteString
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Future

private const val OPEN_DATABASE_COMMAND_SIGNATURE_API_11 =
  "openDatabase" +
    "(" +
    "Ljava/lang/String;" +
    "Landroid/database/sqlite/SQLiteDatabase\$CursorFactory;" +
    "I" +
    "Landroid/database/DatabaseErrorHandler;" +
    ")" +
    "Landroid/database/sqlite/SQLiteDatabase;"

private const val OPEN_DATABASE_COMMAND_SIGNATURE_API_27 =
  "openDatabase" +
    "(" +
    "Ljava/io/File;" +
    "Landroid/database/sqlite/SQLiteDatabase\$OpenParams;" +
    ")" +
    "Landroid/database/sqlite/SQLiteDatabase;"

private const val CREATE_IN_MEMORY_DATABASE_COMMAND_SIGNATURE_API_27 =
  "createInMemory" +
    "(" +
    "Landroid/database/sqlite/SQLiteDatabase\$OpenParams;" +
    ")" +
    "Landroid/database/sqlite/SQLiteDatabase;"

private val OPEN_DATABASE_COMMANDS_LEGACY = listOf(OPEN_DATABASE_COMMAND_SIGNATURE_API_11)

private val OPEN_DATABASE_COMMANDS =
  listOf(
    OPEN_DATABASE_COMMAND_SIGNATURE_API_27,
    OPEN_DATABASE_COMMAND_SIGNATURE_API_11,
    CREATE_IN_MEMORY_DATABASE_COMMAND_SIGNATURE_API_27,
  )

private const val ALL_REFERENCES_RELEASE_COMMAND_SIGNATURE = "onAllReferencesReleased()V"

// SQLiteStatement methods
private val SQLITE_STATEMENT_EXECUTE_METHODS_SIGNATURES: List<String> =
  mutableListOf("execute()V", "executeInsert()J", "executeUpdateDelete()I")

private const val INVALIDATION_MIN_INTERVAL_MS = 1000

// Note: this only works on API26+ because of pragma_* functions
// TODO: replace with a resource file
// language=SQLite
private const val QUERY_TABLE_INFO =
  """
    select
      m.type as type,
      m.name as tableName,
      ti.name as columnName,
      ti.type as columnType,
      [notnull],
      pk,
      ifnull([unique], 0) as [unique]
    from sqlite_master AS m, pragma_table_info(m.name) as ti
    left outer join
      (
        select tableName, name as columnName, ti.[unique]
        from
          (
            select m.name as tableName, il.name as indexName, il.[unique]
            from
              sqlite_master AS m,
              pragma_index_list(m.name) AS il,
              pragma_index_info(il.name) as ii
            where il.[unique] = 1
            group by il.name
            having count(*) = 1  -- countOfColumnsInIndex=1
          )
            as ti,  -- tableName|indexName|unique : unique=1 and countOfColumnsInIndex=1
          pragma_index_info(ti.indexName)
      )
        as tci  -- tableName|columnName|unique : unique=1 and countOfColumnsInIndex=1
      on tci.tableName = m.name and tci.columnName = ti.name
    where m.type in ('table', 'view')
    order by type, tableName, ti.cid  -- cid = columnId
    """

private val HIDDEN_TABLES = setOf("android_metadata", "sqlite_sequence")

/**
 * Inspector to work with SQLite databases
 *
 * TODO(aalbert): Propagate CancellationException where appropriate
 */
internal class SqliteInspector(
  connection: Connection,
  private val environment: InspectorEnvironment,
) : Inspector(connection) {
  private val databaseRegistry =
    DatabaseRegistry(::dispatchDatabaseOpenedEvent, ::dispatchDatabaseClosedEvent)
  private val databaseLockRegistry = DatabaseLockRegistry()
  private val ioExecutor = environment.executors().io()

  /** Utility instance that handles communication with Room's InvalidationTracker instances. */
  private val roomInvalidationRegistry = RoomInvalidationRegistry(environment)

  private val invalidations =
    listOf(
      roomInvalidationRegistry,
      SqlDelightInvalidation.create(environment.artTooling()),
      SqlDelight2Invalidation.create(environment.artTooling()),
    )

  override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
    try {
      val command = Command.parseFrom(data)
      when (command.oneOfCase) {
        TRACK_DATABASES -> handleTrackDatabases(callback)
        GET_SCHEMA -> handleGetSchema(command.getSchema, callback)
        QUERY -> handleQuery(command.query, callback)
        KEEP_DATABASES_OPEN -> handleKeepDatabasesOpen(command.keepDatabasesOpen, callback)
        ACQUIRE_DATABASE_LOCK -> handleAcquireDatabaseLock(command.acquireDatabaseLock, callback)
        RELEASE_DATABASE_LOCK -> handleReleaseDatabaseLock(command.releaseDatabaseLock, callback)
        else ->
          callback.reply(
            createErrorOccurredResponse(
                "Unrecognised command type: " + command.oneOfCase.name,
                null,
                true,
                ERROR_UNRECOGNISED_COMMAND,
              )
              .toByteArray()
          )
      }
    } catch (exception: Throwable) {
      callback.reply(
        createErrorOccurredResponse(
            "Unhandled Exception while processing the command: " + exception.message,
            stackTraceFromException(exception),
            null,
            ERROR_UNKNOWN,
          )
          .toByteArray()
      )
    }
  }

  @Suppress("RedundantOverride")
  override fun onDispose() {
    super.onDispose()
    // TODO(161081452): release database locks and keep-open references
  }

  private fun handleTrackDatabases(callback: CommandCallback) {
    callback.reply(
      Response.newBuilder()
        .setTrackDatabases(TrackDatabasesResponse.getDefaultInstance())
        .build()
        .toByteArray()
    )

    registerReleaseReferenceHooks()
    registerDatabaseOpenedHooks()

    val hookRegistry = EntryExitMatchingHookRegistry(environment)

    registerInvalidationHooks(hookRegistry)
    registerDatabaseClosedHooks(hookRegistry)

    // Check for database instances in memory
    for (instance in environment.artTooling().findInstances(SQLiteDatabase::class.java)) {
      /* the race condition here will be handled by mDatabaseRegistry */
      if (instance.isOpen) {
        onDatabaseOpened(instance)
      } else {
        onDatabaseClosed(instance)
      }
    }

    // Check for database instances on disk
    for (instance in environment.artTooling().findInstances(Application::class.java)) {
      for (name in instance.databaseList()) {
        val path = instance.getDatabasePath(name)
        if (path.exists() && !isHelperSqliteFile(path)) {
          databaseRegistry.notifyOnDiskDatabase(path.absolutePath)
        }
      }
    }
  }

  /**
   * Secures a lock (transaction) on the database. Note that while the lock is in place, no changes
   * to the database are possible: - the lock prevents other threads from modifying the database, -
   * lock thread, on releasing the lock, rolls-back all changes (transaction is rolled-back).
   */
  // code inside the future is exception-proofed
  private fun handleAcquireDatabaseLock(
    command: AcquireDatabaseLockCommand,
    callback: CommandCallback,
  ) {
    val databaseId = command.databaseId
    val connection = acquireConnection(databaseId, callback) ?: return

    // Timeout is covered by mDatabaseLockRegistry
    submit(
      ioExecutor,
      Runnable {
        val lockId: Int
        try {
          lockId = databaseLockRegistry.acquireLock(databaseId, connection.mDatabase)
        } catch (e: Throwable) {
          processLockingException(callback, e, true)
          return@Runnable
        }
        callback.reply(
          Response.newBuilder()
            .setAcquireDatabaseLock(AcquireDatabaseLockResponse.newBuilder().setLockId(lockId))
            .build()
            .toByteArray()
        )
      },
    )
  }

  // code inside the future is exception-proofed
  private fun handleReleaseDatabaseLock(
    command: ReleaseDatabaseLockCommand,
    callback: CommandCallback,
  ) {
    // Timeout is covered by mDatabaseLockRegistry
    submit(
      ioExecutor,
      Runnable {
        try {
          databaseLockRegistry.releaseLock(command.lockId)
        } catch (e: Throwable) {
          processLockingException(callback, e, false)
          return@Runnable
        }
        callback.reply(
          Response.newBuilder()
            .setReleaseDatabaseLock(ReleaseDatabaseLockResponse.getDefaultInstance())
            .build()
            .toByteArray()
        )
      },
    )
  }

  /** @param isLockingStage provide true for acquiring a lock; false for releasing a lock */
  private fun processLockingException(
    callback: CommandCallback,
    exception: Throwable,
    isLockingStage: Boolean,
  ) {
    val errorCode =
      if (((exception is IllegalStateException) && exception.isAttemptAtUsingClosedDatabase()))
        ErrorCode.ERROR_DB_CLOSED_DURING_OPERATION
      else ErrorCode.ERROR_ISSUE_WITH_LOCKING_DATABASE

    val message =
      if (isLockingStage) "Issue while trying to lock the database for the export operation: "
      else "Issue while trying to unlock the database after the export operation: "

    val isRecoverable =
      if (isLockingStage) true // failure to lock the db should be recoverable
      else null // not sure if we can recover from a failure to unlock the db, so

    // UNKNOWN
    callback.reply(
      createErrorOccurredResponse(message, isRecoverable, exception, errorCode).toByteArray()
    )
  }

  /**
   * Tracking potential database closed events via [ ][.ALL_REFERENCES_RELEASE_COMMAND_SIGNATURE]
   */
  private fun registerDatabaseClosedHooks(hookRegistry: EntryExitMatchingHookRegistry) {
    hookRegistry.registerHook(
      SQLiteDatabase::class.java,
      ALL_REFERENCES_RELEASE_COMMAND_SIGNATURE,
    ) { exitFrame ->
      val thisObject = exitFrame.thisObject
      if (thisObject is SQLiteDatabase) {
        onDatabaseClosed(thisObject as SQLiteDatabase?)
      }
    }
  }

  private fun registerDatabaseOpenedHooks() {
    val methods =
      when (Build.VERSION.SDK_INT < 27) {
        true -> OPEN_DATABASE_COMMANDS_LEGACY
        false -> OPEN_DATABASE_COMMANDS
      }

    val hook: ArtTooling.ExitHook<SQLiteDatabase> =
      ArtTooling.ExitHook { database ->
        try {
          onDatabaseOpened(database)
        } catch (exception: Throwable) {
          connection.sendEvent(
            createErrorOccurredEvent(
                "Unhandled Exception while processing an onDatabaseAdded " +
                  "event: " +
                  exception.message,
                stackTraceFromException(exception),
                null,
                ErrorCode.ERROR_ISSUE_WITH_PROCESSING_NEW_DATABASE_CONNECTION,
              )
              .toByteArray()
          )
        }
        database
      }
    for (method in methods) {
      environment.artTooling().registerExitHook(SQLiteDatabase::class.java, method, hook)
    }
  }

  private fun registerReleaseReferenceHooks() {
    environment.artTooling().registerEntryHook(SQLiteClosable::class.java, "releaseReference()V") {
      thisObject,
      _ ->
      if (thisObject is SQLiteDatabase) {
        databaseRegistry.notifyReleaseReference((thisObject as SQLiteDatabase?)!!)
      }
    }
  }

  private fun registerInvalidationHooks(hookRegistry: EntryExitMatchingHookRegistry) {
    /*
     * Schedules a task using {@link mScheduledExecutor} and executes it on {@link mIOExecutor}.
     */
    val deferredExecutor = DeferredExecutor { command, delayMs ->

      // TODO: handle errors from Future
      environment.executors().handler().postDelayed({ ioExecutor.execute(command) }, delayMs)
    }
    val throttler =
      RequestCollapsingThrottler(
        INVALIDATION_MIN_INTERVAL_MS.toLong(),
        { dispatchDatabasePossiblyChangedEvent() },
        deferredExecutor,
      )

    registerInvalidationHooksSqliteStatement(throttler)
    registerInvalidationHooksTransaction(throttler)
    registerInvalidationHooksSQLiteCursor(throttler, hookRegistry)
  }

  /**
   * Triggering invalidation on [SQLiteDatabase.endTransaction] allows us to avoid showing incorrect
   * stale values that could originate from a mid-transaction query.
   *
   * TODO: track if transaction committed or rolled back by observing if
   *   [ ][SQLiteDatabase.setTransactionSuccessful] was called
   */
  private fun registerInvalidationHooksTransaction(throttler: RequestCollapsingThrottler) {
    environment.artTooling().registerExitHook<Any>(
      SQLiteDatabase::class.java,
      "endTransaction()V",
    ) { result ->
      throttler.submitRequest()
      result
    }
  }

  /**
   * Invalidation hooks triggered by:
   * * [SQLiteStatement.execute]
   * * [SQLiteStatement.executeInsert]
   * * [SQLiteStatement.executeUpdateDelete]
   */
  private fun registerInvalidationHooksSqliteStatement(throttler: RequestCollapsingThrottler) {
    for (method in SQLITE_STATEMENT_EXECUTE_METHODS_SIGNATURES) {
      environment.artTooling().registerExitHook<Any>(SQLiteStatement::class.java, method) { result
        ->
        throttler.submitRequest()
        result
      }
    }
  }

  /**
   * Invalidation hooks triggered by [SQLiteCursor.close] which means that the cursor's query was
   * executed.
   *
   * In order to access cursor's query, we also use [SQLiteDatabase.rawQueryWithFactory] which takes
   * a query String and constructs a cursor based on it.
   */
  private fun registerInvalidationHooksSQLiteCursor(
    throttler: RequestCollapsingThrottler,
    hookRegistry: EntryExitMatchingHookRegistry,
  ) {
    // TODO: add active pruning via Cursor#close listener

    val trackedCursors = Collections.synchronizedMap(WeakHashMap<SQLiteCursor, Void?>())

    val rawQueryMethodSignature =
      ("rawQueryWithFactory(" +
        "Landroid/database/sqlite/SQLiteDatabase\$CursorFactory;" +
        "Ljava/lang/String;" +
        "[Ljava/lang/String;" +
        "Ljava/lang/String;" +
        "Landroid/os/CancellationSignal;" +
        ")Landroid/database/Cursor;")
    hookRegistry.registerHook(SQLiteDatabase::class.java, rawQueryMethodSignature) { exitFrame ->
      val cursor = cursorParam(exitFrame.result)
      val query = stringParam(exitFrame.args[1]!!)

      // Only track cursors that might modify the database.
      // TODO: handle PRAGMA select queries, e.g. PRAGMA_TABLE_INFO
      if (
        cursor != null &&
          query != null &&
          DatabaseUtils.getSqlStatementType(query) != DatabaseUtils.STATEMENT_SELECT
      ) {
        trackedCursors[cursor] = null
      }
    }

    environment.artTooling().registerEntryHook(SQLiteCursor::class.java, "close()V") { thisObject, _
      ->
      if (trackedCursors.containsKey(thisObject)) {
        throttler.submitRequest()
      }
    }
  }

  // Gets a SQLiteCursor from a passed-in Object (if possible)
  private fun cursorParam(cursor: Any?): SQLiteCursor? {
    if (cursor is SQLiteCursor) {
      return cursor
    }

    if (cursor is CursorWrapper) {
      return cursorParam(cursor.wrappedCursor)
    }

    // TODO: add support for more cursor types
    Log.w(
      SqliteInspector::class.java.name,
      String.format("Unsupported Cursor type: %s. Invalidation might not work correctly.", cursor),
    )
    return null
  }

  // Gets a String from a passed-in Object (if possible)
  private fun stringParam(string: Any): String? {
    return if (string is String) string else null
  }

  private fun dispatchDatabaseOpenedEvent(databaseId: Int, path: String) {
    connection.sendEvent(
      Event.newBuilder()
        .setDatabaseOpened(DatabaseOpenedEvent.newBuilder().setDatabaseId(databaseId).setPath(path))
        .build()
        .toByteArray()
    )
  }

  private fun dispatchDatabaseClosedEvent(databaseId: Int, path: String) {
    connection.sendEvent(
      Event.newBuilder()
        .setDatabaseClosed(DatabaseClosedEvent.newBuilder().setDatabaseId(databaseId).setPath(path))
        .build()
        .toByteArray()
    )
  }

  private fun dispatchDatabasePossiblyChangedEvent() {
    connection.sendEvent(
      Event.newBuilder()
        .setDatabasePossiblyChanged(DatabasePossiblyChangedEvent.getDefaultInstance())
        .build()
        .toByteArray()
    )
  }

  // code inside the future is exception-proofed
  private fun handleGetSchema(command: GetSchemaCommand, callback: CommandCallback) {
    val connection = acquireConnection(command.databaseId, callback) ?: return

    // TODO: consider a timeout
    submit(connection.mExecutor) { callback.reply(querySchema(connection.mDatabase).toByteArray()) }
  }

  private fun handleQuery(command: QueryCommand, callback: CommandCallback) {
    val connection = acquireConnection(command.databaseId, callback) ?: return

    val cancellationSignal = CancellationSignal()
    val executor = connection.mExecutor
    // TODO: consider a timeout
    val future: Future<*> =
      submit(executor) {
        val params = parseQueryParameterValues(command)
        var cursor: Cursor? = null
        try {
          cursor = rawQuery(connection.mDatabase, command.query, params, cancellationSignal)

          var responseSizeLimitHint = command.responseSizeLimitHint
          // treating unset field as unbounded
          if (responseSizeLimitHint <= 0) responseSizeLimitHint = Long.MAX_VALUE

          val columnNames = listOf(*cursor.columnNames)
          callback.reply(
            Response.newBuilder()
              .setQuery(
                QueryResponse.newBuilder()
                  .addAllRows(convert(cursor, responseSizeLimitHint))
                  .addAllColumnNames(columnNames)
                  .build()
              )
              .build()
              .toByteArray()
          )
          triggerInvalidation(command.query)
        } catch (e: SQLiteException) {
          callback.reply(
            createErrorOccurredResponse(e, true, ErrorCode.ERROR_ISSUE_WITH_PROCESSING_QUERY)
              .toByteArray()
          )
        } catch (e: IllegalArgumentException) {
          callback.reply(
            createErrorOccurredResponse(e, true, ErrorCode.ERROR_ISSUE_WITH_PROCESSING_QUERY)
              .toByteArray()
          )
        } catch (e: IllegalStateException) {
          if (e.isAttemptAtUsingClosedDatabase()) {
            callback.reply(
              createErrorOccurredResponse(e, true, ErrorCode.ERROR_DB_CLOSED_DURING_OPERATION)
                .toByteArray()
            )
          } else {
            callback.reply(createErrorOccurredResponse(e, null, ERROR_UNKNOWN).toByteArray())
          }
        } catch (e: Throwable) {
          callback.reply(createErrorOccurredResponse(e, null, ERROR_UNKNOWN).toByteArray())
        } finally {
          cursor?.close()
        }
      }
    callback.addCancellationListener(environment.executors().primary()) {
      cancellationSignal.cancel()
      future.cancel(true)
    }
  }

  private fun triggerInvalidation(query: String) {
    if (DatabaseUtils.getSqlStatementType(query) != DatabaseUtils.STATEMENT_SELECT) {
      for (invalidation in invalidations) {
        invalidation.triggerInvalidations()
      }
    }
  }

  private fun handleKeepDatabasesOpen(
    keepDatabasesOpen: KeepDatabasesOpenCommand,
    callback: CommandCallback,
  ) {
    // Acknowledge the command
    callback.reply(
      Response.newBuilder()
        .setKeepDatabasesOpen(KeepDatabasesOpenResponse.getDefaultInstance())
        .build()
        .toByteArray()
    )

    databaseRegistry.notifyKeepOpenToggle(keepDatabasesOpen.setEnabled)
  }

  /**
   * Tries to find a database for an id. If no such database is found, it replies with an [ ] via
   * the `callback` provided.
   *
   * The race condition can be mitigated by clients by securing a lock synchronously with no other
   * queries in place.
   *
   * @return null if no database found for the provided id. A database reference otherwise.
   *
   * TODO: remove race condition (affects WAL=off) - lock request is received and in the process of
   *   being secured - query request is received and since no lock in place, receives an IO
   *   Executor - lock request completes and holds a lock on the database - query cannot run because
   *   there is a lock in place
   */
  private fun acquireConnection(databaseId: Int, callback: CommandCallback): DatabaseConnection? {
    val connection = databaseLockRegistry.getConnection(databaseId)
    if (connection != null) {
      // With WAL enabled, we prefer to use the IO executor. With WAL off we don't have a
      // choice and must use the executor that has a lock (transaction) on the database.
      return if (connection.mDatabase.isWriteAheadLoggingEnabled)
        DatabaseConnection(connection.mDatabase, ioExecutor)
      else connection
    }

    val database = databaseRegistry.getConnection(databaseId)
    if (database == null) {
      replyNoDatabaseWithId(callback, databaseId)
      return null
    }

    // Given no lock, IO executor is appropriate.
    return DatabaseConnection(database, ioExecutor)
  }

  private fun replyNoDatabaseWithId(callback: CommandCallback, databaseId: Int) {
    val message =
      String.format(
        "Unable to perform an operation on database (id=%s)." +
          " The database may have already been closed.",
        databaseId,
      )
    callback.reply(
      createErrorOccurredResponse(
          message,
          null,
          true,
          ErrorCode.ERROR_NO_OPEN_DATABASE_WITH_REQUESTED_ID,
        )
        .toByteArray()
    )
  }

  private fun querySchema(database: SQLiteDatabase): Response {
    var cursor: Cursor? = null
    try {
      cursor = rawQuery(database, QUERY_TABLE_INFO, arrayOfNulls(0), null)
      val schemaBuilder = GetSchemaResponse.newBuilder()

      val objectTypeIx = cursor.getColumnIndex("type") // view or table
      val tableNameIx = cursor.getColumnIndex("tableName")
      val columnNameIx = cursor.getColumnIndex("columnName")
      val typeIx = cursor.getColumnIndex("columnType")
      val pkIx = cursor.getColumnIndex("pk")
      val notNullIx = cursor.getColumnIndex("notnull")
      val uniqueIx = cursor.getColumnIndex("unique")

      var tableBuilder: Table.Builder? = null
      while (cursor.moveToNext()) {
        val tableName = cursor.getString(tableNameIx)

        // ignore certain tables
        if (HIDDEN_TABLES.contains(tableName)) {
          continue
        }

        // check if getting data for a new table or appending columns to the current one
        if (tableBuilder == null || tableBuilder.name != tableName) {
          if (tableBuilder != null) {
            schemaBuilder.addTables(tableBuilder.build())
          }
          tableBuilder = Table.newBuilder()
          tableBuilder.setName(tableName)
          tableBuilder.setIsView("view".equals(cursor.getString(objectTypeIx), ignoreCase = true))
        }

        // append column information to the current table info
        tableBuilder!!.addColumns(
          Column.newBuilder()
            .setName(cursor.getString(columnNameIx))
            .setType(cursor.getString(typeIx))
            .setPrimaryKey(cursor.getInt(pkIx))
            .setIsNotNull(cursor.getInt(notNullIx) > 0)
            .setIsUnique(cursor.getInt(uniqueIx) > 0)
            .build()
        )
      }
      if (tableBuilder != null) {
        schemaBuilder.addTables(tableBuilder.build())
      }

      return Response.newBuilder().setGetSchema(schemaBuilder.build()).build()
    } catch (e: IllegalStateException) {
      return if (e.isAttemptAtUsingClosedDatabase()) {
        createErrorOccurredResponse(e, true, ErrorCode.ERROR_DB_CLOSED_DURING_OPERATION)
      } else {
        createErrorOccurredResponse(e, null, ERROR_UNKNOWN)
      }
    } catch (e: Throwable) {
      return createErrorOccurredResponse(e, null, ERROR_UNKNOWN)
    } finally {
      cursor?.close()
    }
  }

  private fun onDatabaseOpened(database: SQLiteDatabase?) {
    roomInvalidationRegistry.invalidateCache()
    databaseRegistry.notifyDatabaseOpened(database!!)
  }

  private fun onDatabaseClosed(database: SQLiteDatabase?) {
    databaseRegistry.notifyAllDatabaseReferencesReleased(database!!)
  }

  @Suppress("SameParameterValue")
  private fun createErrorOccurredEvent(
    message: String?,
    stackTrace: String?,
    isRecoverable: Boolean?,
    errorCode: ErrorCode,
  ): Event {
    return Event.newBuilder()
      .setErrorOccurred(
        ErrorOccurredEvent.newBuilder()
          .setContent(createErrorContentMessage(message, stackTrace, isRecoverable, errorCode))
          .build()
      )
      .build()
  }

  /**
   * Provides a reference to the database and an executor to access the database.
   *
   * Executor is relevant in the context of locking, where a locked database with WAL disabled needs
   * to run queries on the thread that locked it.
   */
  internal class DatabaseConnection(val mDatabase: SQLiteDatabase, val mExecutor: Executor)

  companion object {

    @SuppressLint("Recycle") // For: "The cursor should be freed up after use with #close"
    private fun rawQuery(
      database: SQLiteDatabase,
      queryText: String,
      params: Array<String?>,
      cancellationSignal: CancellationSignal?,
    ): Cursor {
      val cursorFactory =
        SQLiteDatabase.CursorFactory { _, driver, editTable, query ->
          for (i in params.indices) {
            val value = params[i]
            val index = i + 1
            if (value == null) {
              query.bindNull(index)
            } else {
              query.bindString(index, value)
            }
          }
          SQLiteCursor(driver, editTable, query)
        }

      return database.rawQueryWithFactory(cursorFactory, queryText, null, null, cancellationSignal)
    }

    private fun parseQueryParameterValues(command: QueryCommand): Array<String?> {
      val params = arrayOfNulls<String>(command.queryParameterValuesCount)
      for (i in 0 until command.queryParameterValuesCount) {
        val param = command.getQueryParameterValues(i)
        when (param.oneOfCase) {
          QueryParameterValue.OneOfCase.STRING_VALUE -> params[i] = param.stringValue
          QueryParameterValue.OneOfCase.ONEOF_NOT_SET -> params[i] = null
          else ->
            throw IllegalArgumentException(
              "Unsupported parameter type. OneOfCase=" + param.oneOfCase
            )
        }
      }
      return params
    }

    /** @param responseSizeLimitHint expressed in bytes */
    private fun convert(cursor: Cursor?, responseSizeLimitHint: Long): List<Row> {
      var responseSize: Long = 0
      val result: MutableList<Row> = ArrayList()
      val columnCount = cursor!!.columnCount
      while (cursor.moveToNext() && responseSize < responseSizeLimitHint) {
        val rowBuilder = Row.newBuilder()
        for (i in 0 until columnCount) {
          val value = readValue(cursor, i)
          rowBuilder.addValues(value)
        }
        val row = rowBuilder.build()
        // Optimistically adding a row before checking the limit. Eliminates the case when a
        // misconfigured client (limit too low) is unable to fetch any results. Row size in
        // SQLite Android is limited to (~2MB), so the worst case scenario is very manageable.
        result.add(row)
        responseSize += row.serializedSize.toLong()
      }
      return result
    }

    private fun readValue(cursor: Cursor?, index: Int): CellValue {
      val builder = CellValue.newBuilder()

      when (cursor!!.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> {}
        Cursor.FIELD_TYPE_BLOB -> builder.setBlobValue(ByteString.copyFrom(cursor.getBlob(index)))
        Cursor.FIELD_TYPE_STRING -> builder.setStringValue(cursor.getString(index))
        Cursor.FIELD_TYPE_INTEGER -> builder.setLongValue(cursor.getLong(index))
        Cursor.FIELD_TYPE_FLOAT -> builder.setDoubleValue(cursor.getDouble(index))
      }
      return builder.build()
    }

    private fun createErrorContentMessage(
      message: String?,
      stackTrace: String?,
      isRecoverable: Boolean?,
      errorCode: ErrorCode,
    ): ErrorContent {
      val builder = ErrorContent.newBuilder()
      if (message != null) {
        builder.setMessage(message)
      }
      if (stackTrace != null) {
        builder.setStackTrace(stackTrace)
      }
      val recoverability = ErrorRecoverability.newBuilder()
      if (isRecoverable != null) { // leave unset otherwise, which translates to 'unknown'
        recoverability.setIsRecoverable(isRecoverable)
      }
      builder.setRecoverability(recoverability.build())
      builder.setErrorCode(errorCode)
      return builder.build()
    }

    private fun createErrorOccurredResponse(
      exception: Throwable,
      isRecoverable: Boolean?,
      errorCode: ErrorCode,
    ): Response {
      return createErrorOccurredResponse("", isRecoverable, exception, errorCode)
    }

    private fun createErrorOccurredResponse(
      messagePrefix: String,
      isRecoverable: Boolean?,
      exception: Throwable,
      errorCode: ErrorCode,
    ): Response {
      var message = exception.message
      if (message == null) message = exception.toString()
      return createErrorOccurredResponse(
        messagePrefix + message,
        stackTraceFromException(exception),
        isRecoverable,
        errorCode,
      )
    }

    private fun createErrorOccurredResponse(
      message: String?,
      stackTrace: String?,
      isRecoverable: Boolean?,
      errorCode: ErrorCode,
    ): Response {
      return Response.newBuilder()
        .setErrorOccurred(
          ErrorOccurredResponse.newBuilder()
            .setContent(createErrorContentMessage(message, stackTrace, isRecoverable, errorCode))
        )
        .build()
    }

    private fun stackTraceFromException(exception: Throwable): String {
      val writer = StringWriter()
      exception.printStackTrace(PrintWriter(writer))
      return writer.toString()
    }

    private fun isHelperSqliteFile(file: File): Boolean {
      val path = file.path
      return path.endsWith("-journal") || path.endsWith("-shm") || path.endsWith("-wal")
    }
  }
}
