package org.tkit.liquibase;

import com.datical.liquibase.ext.appdba.diff.compare.SynonymComparator;
import com.datical.liquibase.ext.appdba.markunused.change.MarkUnsedChange;
import com.datical.liquibase.ext.appdba.markunused.change.MarkUnusedGenerator;
import com.datical.liquibase.ext.appdba.synonym.SynonymSnapshotGenerator;
import com.datical.liquibase.ext.appdba.synonym.change.CreateSynonymChange;
import com.datical.liquibase.ext.appdba.synonym.change.CreateSynonymGenerator;
import com.datical.liquibase.ext.appdba.synonym.change.DropSynonymChange;
import com.datical.liquibase.ext.appdba.synonym.change.DropSynonymGenerator;
import com.datical.liquibase.ext.parser.LiquibaseProNamespaceDetails;
import com.datical.liquibase.ext.storedlogic.StoredLogicComparator;
import com.datical.liquibase.ext.storedlogic.checkconstraint.CheckConstraintComparator;
import com.datical.liquibase.ext.storedlogic.checkconstraint.CheckConstraintSnapshotGenerator;
import com.datical.liquibase.ext.storedlogic.checkconstraint.change.*;
import com.datical.liquibase.ext.storedlogic.checkconstraint.postgres.PostgresCheckConstraintSnapshotGenerator;
import com.datical.liquibase.ext.storedlogic.databasepackage.PackageBodySnapshotGenerator;
import com.datical.liquibase.ext.storedlogic.databasepackage.PackageSnapshotGenerator;
import com.datical.liquibase.ext.storedlogic.databasepackage.change.*;
import com.datical.liquibase.ext.storedlogic.function.FunctionSnapshotGenerator;
import com.datical.liquibase.ext.storedlogic.function.change.CreateFunctionChange;
import com.datical.liquibase.ext.storedlogic.function.change.CreateFunctionGenerator;
import com.datical.liquibase.ext.storedlogic.function.change.DropFunctionChange;
import com.datical.liquibase.ext.storedlogic.function.change.DropFunctionGenerator;
import com.datical.liquibase.ext.storedlogic.function.postgres.EDBPostgresFunctionSnapshotGenerator;
import com.datical.liquibase.ext.storedlogic.function.postgres.PostgresFunctionSnapshotGenerator;
import com.datical.liquibase.ext.storedlogic.storedproc.PostgresStoredProcedureSnapshotGenerator;
import com.datical.liquibase.ext.storedlogic.storedproc.StoredProcedureSnapshotGenerator;
import com.datical.liquibase.ext.storedlogic.trigger.TriggerSnapshotGenerator;
import com.datical.liquibase.ext.storedlogic.trigger.change.*;
import com.datical.liquibase.ext.storedlogic.trigger.postgres.PostgresTriggerSnapshotGenerator;
import com.oracle.svm.core.annotate.*;
import com.oracle.svm.core.jdk.LocalizationSupport;
import liquibase.change.core.*;
import liquibase.change.custom.CustomChangeWrapper;
import liquibase.changelog.StandardChangeLogHistoryService;
import liquibase.database.core.*;
import liquibase.datatype.LiquibaseDataType;
import liquibase.datatype.core.*;
import liquibase.diff.compare.core.*;
import liquibase.exception.ServiceNotFoundException;
import liquibase.executor.jvm.JdbcExecutor;
import liquibase.lockservice.StandardLockService;
import liquibase.logging.LogService;
import liquibase.logging.LogType;
import liquibase.parser.core.formattedsql.FormattedSqlChangeLogParser;
import liquibase.parser.core.sql.SqlChangeLogParser;
import liquibase.parser.core.xml.StandardNamespaceDetails;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;
import liquibase.precondition.CustomPreconditionWrapper;
import liquibase.precondition.core.*;
import liquibase.pro.packaged.kp;
import liquibase.sdk.database.MockDatabase;
import liquibase.snapshot.jvm.*;
import liquibase.sqlgenerator.core.*;
import liquibase.util.NetUtil;
import liquibase.util.xml.XMLResourceBundleFactory;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.postgresql.Driver;
import org.postgresql.core.PGStream;
import org.postgresql.core.v3.ConnectionFactoryImpl;
import org.postgresql.sspi.ISSPIClient;
import org.postgresql.sspi.NTDSAPIWrapper;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

@TargetClass(LocalizationSupport.class)
final class Target_LocalizationSupport {

    @Alias
    protected Map<String, ResourceBundle> resourceBundles;

    @Substitute
    public ResourceBundle getCached(String baseName, Locale locale) throws MissingResourceException {
        ResourceBundle result = resourceBundles.get(baseName);
        if (result == null && JdkSubstitutions.BUNDLE_NAME.equals(baseName)) {
            try (InputStream in = Target_LocalizationSupport.class.getResourceAsStream(JdkSubstitutions.BUNDLE_FILE)) {
                resourceBundles.put(baseName, XMLResourceBundleFactory.create(in));
                return resourceBundles.get(baseName);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (result == null) {
            String errorMessage = "Resource bundle not found " + baseName + ". " +
                    "Register the resource bundle using the option " + baseName + ".";
            throw new MissingResourceException(errorMessage, this.getClass().getName(), baseName);
        }
        return result;
    }
}

@TargetClass(NetUtil.class)
final class Target_NetUtil {

    @Substitute
    public static String getLocalHostName() throws UnknownHostException, SocketException {
        try {
            String hostname = System.getenv("HOSTNAME");
            if (hostname != null && !hostname.isEmpty()) {
                return hostname;
            }
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            LogService.getLog(NetUtil.class).debug(LogType.LOG, "Error getting hostname", e);
            return "unknown";
        }
    }

    @Substitute
    public static String getLocalHostAddress() throws UnknownHostException, SocketException {
        try {
            String address = System.getenv("KUBERNETES_SERVICE_HOST");
            if (address != null && !address.isEmpty()) {
                return address;
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            LogService.getLog(NetUtil.class).debug(LogType.LOG, "Error getting host address", e);
            return "unknown";
        }
    }

}

@TargetClass(liquibase.servicelocator.ServiceLocator.class)
final class Target_ServiceLocator {

    @Alias
    private Map<Class, List<Class>> classesBySuperclass;

    @Substitute
    public <T> Class<? extends T>[] findClasses(Class<T> requiredInterface) throws ServiceNotFoundException {
        LogService.getLog(Target_ServiceLocator.class).info(LogType.LOG, "Loading the service interface " + requiredInterface);
        if (liquibase.license.LicenseService.class.equals(requiredInterface)) {
            return new Class[]{
//                    kp.class
            };
        }
        if (liquibase.diff.compare.DatabaseObjectComparator.class.equals(requiredInterface)) {
            return new Class[]{
                    CheckConstraintComparator.class,
                    TableComparator.class,
                    SynonymComparator.class,
                    DefaultDatabaseObjectComparator.class,
                    ForeignKeyComparator.class,
                    ColumnComparator.class,
                    UniqueConstraintComparator.class,
                    IndexComparator.class,
                    PrimaryKeyComparator.class,
                    CatalogComparator.class,
                    SchemaComparator.class,
                    StoredLogicComparator.class
            };
        }
        if (liquibase.parser.NamespaceDetails.class.equals(requiredInterface)) {
            return new Class[]{
                    LiquibaseProNamespaceDetails.class,
                    StandardNamespaceDetails.class
            };
        }
        if (liquibase.precondition.Precondition.class.equals(requiredInterface)) {
            return new Class[]{
                    IndexExistsPrecondition.class,
                    CustomPreconditionWrapper.class,
                    ForeignKeyExistsPrecondition.class,
                    PrimaryKeyExistsPrecondition.class,
                    DBMSPrecondition.class,
                    SequenceExistsPrecondition.class,
                    RowCountPrecondition.class,
                    TableIsEmptyPrecondition.class,
                    ChangeLogPropertyDefinedPrecondition.class,
                    SqlPrecondition.class,
                    TableExistsPrecondition.class,
                    ChangeSetExecutedPrecondition.class,
                    RunningAsPrecondition.class,
                    ViewExistsPrecondition.class,
                    ColumnExistsPrecondition.class,
                    NotPrecondition.class,
                    AndPrecondition.class,
                    PreconditionContainer.class,
                    OrPrecondition.class,
                    ObjectQuotingStrategyPrecondition.class,
            };
        }
        if (liquibase.database.Database.class.equals(requiredInterface)) {
            return new Class[]{
                    MockDatabase.class,
                    SQLiteDatabase.class,
                    Ingres9Database.class,
                    DerbyDatabase.class,
                    FirebirdDatabase.class,
                    Firebird3Database.class,
                    H2Database.class,
                    MSSQLDatabase.class,
                    SybaseDatabase.class,
                    OracleDatabase.class,
                    InformixDatabase.class,
                    SybaseASADatabase.class,
                    DB2Database.class,
                    Db2zDatabase.class,
                    HsqlDatabase.class,
                    UnsupportedDatabase.class,
                    MySQLDatabase.class,
                    MariaDBDatabase.class,
                    PostgresDatabase.class
            };
        }
        if (liquibase.change.Change.class.equals(requiredInterface)) {
            return new Class[]{
                    DropViewChange.class,
                    AddUniqueConstraintChange.class,
                    DropColumnChange.class,
                    AddAutoIncrementChange.class,
                    DropIndexChange.class,
                    AddForeignKeyConstraintChange.class,
                    ModifyDataTypeChange.class,
                    DropNotNullConstraintChange.class,
                    CustomChangeWrapper.class,
                    StopChange.class,
                    TagDatabaseChange.class,
                    RenameTableChange.class,
                    MergeColumnChange.class,
                    EmptyChange.class,
                    OutputChange.class,
                    LoadDataChange.class,
                    RawSQLChange.class,
                    SQLFileChange.class,

                    SetTableRemarksChange.class,
                    AlterSequenceChange.class,
                    ExecuteShellCommandChange.class,
                    CreateIndexChange.class,
                    RenameViewChange.class,
                    InsertDataChange.class,
                    DropPrimaryKeyChange.class,
                    DropUniqueConstraintChange.class,
                    DropSequenceChange.class,
                    RenameSequenceChange.class,
                    CreateSequenceChange.class,
                    DropDefaultValueChange.class,
                    AddNotNullConstraintChange.class,
                    AddColumnChange.class,
                    DropTableChange.class,
                    AddLookupTableChange.class,

                    UpdateDataChange.class,
                    DeleteDataChange.class,
                    RawSQLChange.class,
                    SQLFileChange.class,

                    DropAllForeignKeyConstraintsChange.class,
                    SetColumnRemarksChange.class,
                    CreateViewChange.class,

                    MarkUnsedChange.class,
                    RenameTriggerChange.class,
                    DropPackageBodyChange.class,
                    CreateSynonymChange.class,
                    DropSynonymChange.class,
                    DisableTriggerChange.class,
                    DropFunctionChange.class,
                    DisableCheckConstraintChange.class,
                    DropTriggerChange.class,
                    EnableTriggerChange.class,
                    EnableCheckConstraintChange.class,
                    AddCheckConstraintChange.class,
                    DropPackageChange.class,
                    DropCheckConstraintChange.class,

                    CreateTableChange.class,
                    RenameColumnChange.class,
                    DropForeignKeyConstraintChange.class,
                    DropProcedureChange.class,
                    CreateProcedureChange.class,
                    CreatePackageChange.class,
                    CreateFunctionChange.class,
                    CreateTriggerChange.class,
                    CreatePackageBodyChange.class,
                    AddPrimaryKeyChange.class,
                    AddDefaultValueChange.class
            };
        }
        if (liquibase.snapshot.SnapshotGenerator.class.equals(requiredInterface)) {
            return new Class[]{
                    TableSnapshotGenerator.class,
                    ForeignKeySnapshotGenerator.class,
                    PrimaryKeySnapshotGenerator.class,
                    CatalogSnapshotGenerator.class,
                    IndexSnapshotGenerator.class,
                    SequenceSnapshotGenerator.class,
                    SynonymSnapshotGenerator.class,
                    CheckConstraintSnapshotGenerator.class,
                    PostgresCheckConstraintSnapshotGenerator.class,
                    StoredProcedureSnapshotGenerator.class,
                    PackageBodySnapshotGenerator.class,
                    PostgresStoredProcedureSnapshotGenerator.class,
                    TriggerSnapshotGenerator.class,
                    PostgresTriggerSnapshotGenerator.class,
                    PostgresFunctionSnapshotGenerator.class,
                    EDBPostgresFunctionSnapshotGenerator.class,
                    PackageSnapshotGenerator.class,
                    FunctionSnapshotGenerator.class,
                    DataSnapshotGenerator.class,
                    ColumnSnapshotGenerator.class,
                    ColumnSnapshotGeneratorInformix.class,
                    ColumnSnapshotGeneratorOracle.class,
                    ColumnSnapshotGeneratorPostgres.class,
                    ColumnSnapshotGeneratorH2.class,
                    ViewSnapshotGenerator.class,
                    UniqueConstraintSnapshotGenerator.class,
                    SchemaSnapshotGenerator.class
            };
        }
        if (liquibase.parser.ChangeLogParser.class.equals(requiredInterface)) {
            return new Class[]{
                    FormattedSqlChangeLogParser.class,
                    SqlChangeLogParser.class,
                    XMLChangeLogSAXParser.class
            };
        }
        if (liquibase.changelog.ChangeLogHistoryService.class.equals(requiredInterface)) {
            return new Class[]{
                    StandardChangeLogHistoryService.class
//                    OfflineChangeLogHistoryService.class
            };
        }
        if (LiquibaseDataType.class.equals(requiredInterface)) {
            return new Class[]{
                    DateType.class,
                    IntType.class,
                    ClobType.class,
                    BlobType.class,
                    UnknownType.class,
                    MediumIntType.class,
                    CurrencyType.class,
                    DoubleType.class,
//                    DataTypeWrapper.class,
                    BigIntType.class,
                    TinyIntType.class,
                    BooleanType.class,
                    XMLType.class,
                    CharType.class,
                    VarcharType.class,
                    NCharType.class,
                    NVarcharType.class,
                    DateTimeType.class,
                    TimestampType.class,
                    TimeType.class,
                    UUIDType.class,
                    FloatType.class,
                    SmallIntType.class,
                    NumberType.class,
                    DatabaseFunctionType.class,
                    DecimalType.class
            };
        }
        if (liquibase.executor.Executor.class.equals(requiredInterface)) {
            return new Class[]{JdbcExecutor.class};
        }
        if (liquibase.lockservice.LockService.class.equals(requiredInterface)) {
            return new Class[]{StandardLockService.class};
        }
        if (liquibase.sqlgenerator.SqlGenerator.class.equals(requiredInterface)) {
            return new Class[]{
                    RenameColumnGenerator.class,
                    SelectFromDatabaseChangeLogLockGenerator.class,
                    StoredProcedureGenerator.class,
                    SetTableRemarksGenerator.class,
                    MarkChangeSetRanGenerator.class,
                    DropPackageBodyGenerator.class,
                    SetColumnRemarksGenerator.class,
                    CreateTableGenerator.class,
                    AddAutoIncrementGenerator.class,
                    UpdateDataChangeGenerator.class,
                    DropColumnGenerator.class,
                    TableRowCountGenerator.class,
                    DropIndexGenerator.class,
                    TableRowCountGenerator.class,
                    DropIndexGenerator.class,
                    AddColumnGenerator.class,
                    AddColumnGeneratorDefaultClauseBeforeNotNull.class,
                    CopyRowsGenerator.class,
                    AddForeignKeyConstraintGenerator.class,
                    InsertDataChangeGenerator.class,
                    InsertGenerator.class,
                    SelectFromDatabaseChangeLogGenerator.class,
                    UpdateGenerator.class,
                    DropForeignKeyConstraintGenerator.class,
                    CreateDatabaseChangeLogTableGenerator.class,
                    RenameViewGenerator.class,
                    AddUniqueConstraintGenerator.class,
                    MarkUnusedGenerator.class,
                    DropCheckConstraintGenerator.class,
                    DropTableGenerator.class,
                    CreateFunctionGenerator.class,
                    DropSequenceGenerator.class,
                    UpdateChangeSetChecksumGenerator.class,
                    DropFunctionGenerator.class,
                    LockDatabaseChangeLogGenerator.class,
                    CreateViewGenerator.class,
                    InsertDataChangeGenerator.class,
                    RenameSequenceGenerator.class,
                    InsertOrUpdateGeneratorPostgres.class,
                    BatchDmlExecutablePreparedStatementGenerator.class,
                    DropUniqueConstraintGenerator.class,
                    AlterSequenceGenerator.class,
                    DropTriggerGenerator.class,
                    CreatePackageBodyGenerator.class,
                    DropViewGenerator.class,
                    DisableCheckConstraintGenerator.class,
                    DropPrimaryKeyGenerator.class,
                    ClearDatabaseChangeLogTableGenerator.class,
                    EnableTriggerGenerator.class,
                    DropDefaultValueGenerator.class,
                    CreatePackageGenerator.class,

                    AddCheckConstraintGenerator.class,
                    AddDefaultValueGeneratorPostgres.class,
                    DropProcedureGenerator.class,
                    CreateDatabaseChangeLogLockTableGenerator.class,
                    AddPrimaryKeyGenerator.class,
                    DisableTriggerGenerator.class,
                    SetNullableGenerator.class,
                    liquibase.sqlgenerator.core.RenameTableGenerator.class,
                    DropPackageBodyGenerator.class,
                    CreateProcedureGenerator.class,
                    CreateIndexGeneratorPostgres.class,
                    InsertSetGenerator.class,
                    CommentGenerator.class,
                    GetViewDefinitionGeneratorPostgres.class,
                    ModifyDataTypeGenerator.class,
                    RemoveChangeSetRanStatusGenerator.class,
                    RuntimeGenerator.class,
                    CreateSynonymGenerator.class,
                    RenameTableGenerator.class,
                    EnableCheckConstraintGenerator.class,
                    RawSqlGenerator.class,
                    CreateTriggerGenerator.class,
                    UnlockDatabaseChangeLogGenerator.class,
                    InitializeDatabaseChangeLogLockTableGenerator.class,
                    TagDatabaseGenerator.class,
                    DeleteGenerator.class,

                    CreateSequenceGenerator.class,
                    DropSynonymGenerator.class,
                    GetNextChangeSetSequenceValueGenerator.class
            };
        }

        LogService.getLog(getClass()).debug(LogType.LOG, "ServiceLocator.findClasses for " + requiredInterface.getName());

        try {
            Class.forName(requiredInterface.getName());

            if (!classesBySuperclass.containsKey(requiredInterface)) {
                classesBySuperclass.put(requiredInterface, this.findClassesImpl(requiredInterface));
            }
        } catch (Exception e) {
            throw new ServiceNotFoundException(e);
        }

        List<Class> classes = classesBySuperclass.get(requiredInterface);
        HashSet<Class> uniqueClasses = new HashSet<>(classes);
        return uniqueClasses.toArray(new Class[uniqueClasses.size()]);
    }

    @Alias
    private List<Class> findClassesImpl(Class requiredInterface) throws Exception {
        return null;
    }
}

@AutomaticFeature
class RuntimeReflectionRegistrationFeature implements Feature {
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        RuntimeReflection.register(java.sql.Statement[].class);
    }
}

@TargetClass(NTDSAPIWrapper.class)
@Delete
final class Remove_NTDSAPIWrapper {

}

@TargetClass(Driver.class)
final class DriverSubstitutions {

    @Substitute
    private void setupLoggerFromProperties(final Properties props) {
        //We don't want it to mess with the logger config
    }

}

@TargetClass(ConnectionFactoryImpl.class)
final class DisableSSPIClient {

    @Substitute
    private ISSPIClient createSSPI(PGStream pgStream,
                                   String spnServiceClass,
                                   boolean enableNegotiate) {
        throw new IllegalStateException("The org.postgresql.sspi.SSPIClient is not available on GraalVM");
    }

}

public class JdkSubstitutions {

    static final String BUNDLE_NAME = "liquibase/i18n/liquibase-commandline-helptext";

    static final String BUNDLE_FILE = "/" + BUNDLE_NAME + ".xml";

}
