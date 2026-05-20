package com.ddlgen;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Oracle DDL Generator
 * 
 * Connects to an Oracle database, extracts DDL for all objects in a target schema,
 * and generates deployment-ready SQL scripts with DROP-IF-EXISTS + CREATE statements.
 * 
 * Output is organized by object type in dependency order, with a master_run.sql
 * that executes everything sequentially via SQL*Plus.
 * 
 * Usage:
 *   mvn exec:java
 *   -- or --
 *   mvn package && java -jar target/oracle-ddl-generator-1.0.0.jar
 */
public class DdlGenerator {

    // ========================================================================
    // Object types in strict dependency/creation order
    // ========================================================================
    enum OracleObjectType {
        SEQUENCE   ("01_sequences",          "SEQUENCE",           -2289, "DROP SEQUENCE"),
        TYPE       ("02_types",              "TYPE",               -4043, "DROP TYPE"),
        TABLE      ("03_tables",             "TABLE",               -942, "DROP TABLE"),
        INDEX      ("04_indexes",            "INDEX",              -1418, "DROP INDEX"),
        VIEW       ("05_views",              "VIEW",                -942, "DROP VIEW"),
        MVIEW      ("06_materialized_views", "MATERIALIZED VIEW", -12003, "DROP MATERIALIZED VIEW"),
        FUNCTION   ("07_functions",          "FUNCTION",           -4043, "DROP FUNCTION"),
        PROCEDURE  ("08_procedures",         "PROCEDURE",          -4043, "DROP PROCEDURE"),
        PACKAGE    ("09_packages",           "PACKAGE",            -4043, "DROP PACKAGE"),
        TRIGGER    ("10_triggers",           "TRIGGER",            -4080, "DROP TRIGGER"),
        SYNONYM    ("11_synonyms",           "SYNONYM",            -1434, "DROP SYNONYM"),
        DB_LINK    ("12_db_links",           "DB_LINK",            -2024, "DROP DATABASE LINK");

        final String folder;
        final String oracleType;
        final int    dropErrorCode;   // ORA- code to suppress on DROP (object not found)
        final String dropPrefix;

        OracleObjectType(String folder, String oracleType, int dropErrorCode, String dropPrefix) {
            this.folder        = folder;
            this.oracleType    = oracleType;
            this.dropErrorCode = dropErrorCode;
            this.dropPrefix    = dropPrefix;
        }
    }

    // ========================================================================
    // Configuration
    // ========================================================================
    private final String  dbUrl;
    private final String  dbUser;
    private final String  dbPassword;
    private final String  targetSchema;
    private final Path    outputRoot;
    private final boolean includeStorage;
    private final boolean includeGrants;

    // Stats
    private final Map<OracleObjectType, Integer> stats = new LinkedHashMap<>();
    private int totalObjects = 0;

    public DdlGenerator(Properties props) {
        this.dbUrl          = props.getProperty("db.url");
        this.dbUser         = props.getProperty("db.username");
        this.dbPassword     = props.getProperty("db.password");
        this.targetSchema   = props.getProperty("target.schema", dbUser).toUpperCase();
        this.outputRoot     = Paths.get(props.getProperty("output.dir", "./ddl_output"), targetSchema);
        this.includeStorage = Boolean.parseBoolean(props.getProperty("include.storage", "false"));
        this.includeGrants  = Boolean.parseBoolean(props.getProperty("include.grants", "true"));
    }

    // ========================================================================
    // Entry point
    // ========================================================================
    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("  Oracle DDL Generator");
        System.out.println("==========================================================");

        try {
            Properties props = loadConfig(args);
            DdlGenerator generator = new DdlGenerator(props);
            generator.run();
        } catch (Exception e) {
            System.err.println("\n[FATAL] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Load configuration: command-line path takes priority, then classpath default.
     */
    private static Properties loadConfig(String[] args) throws IOException {
        Properties props = new Properties();
        if (args.length > 0) {
            System.out.println("[CONFIG] Loading from: " + args[0]);
            try (InputStream is = new FileInputStream(args[0])) {
                props.load(is);
            }
        } else {
            System.out.println("[CONFIG] Loading from classpath: application.properties");
            try (InputStream is = DdlGenerator.class.getClassLoader()
                    .getResourceAsStream("application.properties")) {
                if (is == null) throw new FileNotFoundException("application.properties not found on classpath");
                props.load(is);
            }
        }
        return props;
    }

    // ========================================================================
    // Main workflow
    // ========================================================================
    public void run() throws Exception {
        System.out.println("[INFO]   Target schema : " + targetSchema);
        System.out.println("[INFO]   Output dir    : " + outputRoot.toAbsolutePath());
        System.out.println("[INFO]   Storage clauses: " + (includeStorage ? "INCLUDED" : "STRIPPED"));
        System.out.println("[INFO]   Grants        : " + (includeGrants ? "INCLUDED" : "SKIPPED"));
        System.out.println();

        // Prepare output directories
        prepareOutputDirs();

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            System.out.println("[OK]     Connected to: " + dbUrl);

            // Configure DBMS_METADATA session transforms
            configureMetadata(conn);

            // Extract DDL for each object type in order
            for (OracleObjectType objType : OracleObjectType.values()) {
                extractObjectType(conn, objType);
            }

            // Generate grants script if enabled
            if (includeGrants) {
                extractGrants(conn);
            }

            // Generate the master deployment script
            generateMasterScript();
        }

        printSummary();
    }

    // ========================================================================
    // Output directory setup
    // ========================================================================
    private void prepareOutputDirs() throws IOException {
        // Clean and recreate
        if (Files.exists(outputRoot)) {
            System.out.println("[INFO]   Cleaning existing output directory...");
            try (var walk = Files.walk(outputRoot)) {
                walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        }

        for (OracleObjectType objType : OracleObjectType.values()) {
            Files.createDirectories(outputRoot.resolve(objType.folder));
        }
        Files.createDirectories(outputRoot.resolve("13_grants"));
        System.out.println("[OK]     Output directories created.");
    }

    // ========================================================================
    // DBMS_METADATA session configuration
    // ========================================================================
    private void configureMetadata(Connection conn) throws SQLException {
        try (CallableStatement cs = conn.prepareCall(
                "BEGIN " +
                "  DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'SQLTERMINATOR', TRUE); " +
                "  DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'PRETTY', TRUE); " +
                "  DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'SEGMENT_ATTRIBUTES', :storage); " +
                "  DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'TABLESPACE', :tbs); " +
                "  DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'STORAGE', :stg); " +
                "  DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'CONSTRAINTS', TRUE); " +
                "  DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'REF_CONSTRAINTS', TRUE); " +
                "  DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'CONSTRAINTS_AS_ALTER', TRUE); " +
                "END;")) {
            cs.setBoolean("storage", includeStorage);
            cs.setBoolean("tbs",     includeStorage);
            cs.setBoolean("stg",     includeStorage);
            cs.execute();
        }
        System.out.println("[OK]     DBMS_METADATA session configured.");
    }

    /**
     * Build the query to discover objects. Uses ALL_OBJECTS for most types,
     * but special-cases indexes (skip LOB/system indexes) and synonyms.
     */
    private String buildObjectLookupSql(OracleObjectType objType) {
        return switch (objType) {
            case INDEX -> """
                SELECT index_name FROM all_indexes
                WHERE owner = ? AND index_type != 'LOB'
                  AND generated = 'N'
                ORDER BY index_name
                """;
            case SYNONYM -> """
                SELECT synonym_name FROM all_synonyms
                WHERE owner = ?
                ORDER BY synonym_name
                """;
            case DB_LINK -> """
                SELECT db_link FROM all_db_links
                WHERE owner = ?
                ORDER BY db_link
                """;
            case MVIEW -> """
                SELECT mview_name FROM all_mviews
                WHERE owner = ?
                ORDER BY mview_name
                """;
            case TABLE -> """
                SELECT table_name FROM all_tables
                WHERE owner = ? AND temporary = 'N'
                  AND table_name NOT LIKE 'SYS_%'
                  AND table_name NOT LIKE 'MLOG$%'
                  AND table_name NOT LIKE 'RUPD$%'
                  AND table_name NOT LIKE 'BIN$%'
                ORDER BY table_name
                """;
            default -> """
                SELECT object_name FROM all_objects
                WHERE owner = ? AND object_type = ?
                ORDER BY object_name
                """;
        };
    }

    /**
     * Extract DDL using DBMS_METADATA.GET_DDL.
     */
    private String getDdl(Connection conn, OracleObjectType objType, String objName) {
        // DBMS_METADATA uses specific type strings
        String metaType = objType.oracleType.replace(" ", "_");

        String sql = "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) FROM DUAL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, metaType);
            ps.setString(2, objName);
            ps.setString(3, targetSchema);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Clob clob = rs.getClob(1);
                    if (clob != null) {
                        String ddl = clob.getSubString(1, (int) clob.length());
                        clob.free();
                        return ddl.strip();
                    }
                }
            }
        } catch (SQLException e) {
            // Some objects (e.g., system-generated) may fail — log and skip
            System.out.printf("  [WARN] DBMS_METADATA error for %s %s.%s: %s%n",
                    objType.oracleType, targetSchema, objName, e.getMessage().lines().findFirst().orElse(""));
        }
        return null;
    }

    // ========================================================================
    // Script generation: DROP IF EXISTS + CREATE
    // ========================================================================
    private String buildScript(OracleObjectType objType, String objName, String ddl) {
        StringBuilder sb = new StringBuilder();
        sb.append(scriptHeader(objType, objName));
        sb.append(buildDropBlock(objType, objName));
        sb.append("\n");
        sb.append("-- =============================================================\n");
        sb.append("-- CREATE\n");
        sb.append("-- =============================================================\n");
        sb.append(cleanDdl(ddl));
        sb.append("\n\n");
        return sb.toString();
    }

    private String scriptHeader(OracleObjectType objType, String objName) {
        return String.format("""
                -- =============================================================
                -- %s: %s.%s
                -- Generated: %s
                -- =============================================================
                
                """,
                objType.oracleType, targetSchema, objName,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    /**
     * Generate a PL/SQL anonymous block that safely drops the object,
     * suppressing the "object not found" ORA error.
     */
    private String buildDropBlock(OracleObjectType objType, String objName) {
        String qualifiedName = targetSchema + "." + objName;
        String dropStmt = objType.dropPrefix + " " + qualifiedName;

        // Tables need CASCADE CONSTRAINTS to avoid FK issues
        if (objType == OracleObjectType.TABLE) {
            dropStmt += " CASCADE CONSTRAINTS PURGE";
        }

        return String.format("""
                -- DROP IF EXISTS
                BEGIN
                  EXECUTE IMMEDIATE '%s';
                  DBMS_OUTPUT.PUT_LINE('Dropped %s %s');
                EXCEPTION
                  WHEN OTHERS THEN
                    IF SQLCODE != %d THEN RAISE; END IF;
                    DBMS_OUTPUT.PUT_LINE('%s %s does not exist - skipping drop');
                END;
                /
                """,
                dropStmt.replace("'", "''"),
                objType.oracleType, qualifiedName,
                objType.dropErrorCode,
                objType.oracleType, qualifiedName);
    }

    /**
     * Clean up DDL output from DBMS_METADATA.
     */
    private String cleanDdl(String ddl) {
        // Remove double-quoted schema prefix if it matches our target (cleaner scripts)
        // Keep it if you want fully qualified DDL — comment this out
        String cleaned = ddl;

        // Ensure it ends with a SQL terminator
        cleaned = cleaned.stripTrailing();
        if (!cleaned.endsWith(";") && !cleaned.endsWith("/")) {
            // PL/SQL objects (procedures, functions, packages, triggers) end with /
            cleaned += "\n/";
        }

        return cleaned;
    }

    // ========================================================================
    // Grant extraction
    // ========================================================================
    private void extractGrants(Connection conn) throws Exception {
        String sql = """
            SELECT DBMS_METADATA.GET_GRANTED_DDL('OBJECT_GRANT', ?) FROM DUAL
            """;

        String grantDdl = null;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, targetSchema);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Clob clob = rs.getClob(1);
                    if (clob != null) {
                        grantDdl = clob.getSubString(1, (int) clob.length());
                        clob.free();
                    }
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 31608) {
                System.out.println("[SKIP]   GRANTS -- no object grants found");
                return;
            }
            System.out.println("[WARN]   GRANTS -- " + e.getMessage().lines().findFirst().orElse(""));
            return;
        }

        if (grantDdl != null && !grantDdl.isBlank()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("""
                    -- =============================================================
                    -- OBJECT GRANTS for schema: %s
                    -- Generated: %s
                    -- =============================================================
                    
                    """,
                    targetSchema,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            sb.append(grantDdl.strip());
            sb.append("\n\n");

            Path grantFile = outputRoot.resolve("13_grants").resolve("object_grants.sql");
            Files.writeString(grantFile, sb.toString());
            System.out.println("[EXPORT] GRANTS -- object_grants.sql written");
        }
    }

    // ========================================================================
    // Master deployment script generation
    // ========================================================================
    private void generateMasterScript() throws IOException {
        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        sb.append(String.format("""
                -- =============================================================
                -- MASTER DEPLOYMENT SCRIPT
                -- Schema : %s
                -- Generated: %s
                -- =============================================================
                --
                -- Execution Order (dependency-safe):
                --   1.  Sequences       (no dependencies)
                --   2.  Types           (may depend on other types)
                --   3.  Tables          (depend on sequences, types)
                --   4.  Indexes         (depend on tables)
                --   5.  Views           (depend on tables)
                --   6.  Materialized Views
                --   7.  Functions       (depend on tables/views)
                --   8.  Procedures      (depend on tables/views/functions)
                --   9.  Packages        (depend on all above)
                --   10. Triggers        (depend on tables)
                --   11. Synonyms        (depend on all objects)
                --   12. Database Links
                --   13. Grants          (depend on all objects)
                --
                -- Usage:
                --   sqlplus user/password@db @master_run.sql
                -- =============================================================
                
                SET SERVEROUTPUT ON SIZE UNLIMITED
                SET ECHO ON
                SET FEEDBACK ON
                SET TIMING ON
                SET LINESIZE 200
                WHENEVER SQLERROR CONTINUE
                
                -- Deployment spool log
                SPOOL master_run_%s.log
                
                PROMPT ============================================================
                PROMPT   DEPLOYMENT START: %s
                PROMPT   Schema: %s
                PROMPT ============================================================
                
                """,
                targetSchema, timestamp,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
                timestamp, targetSchema));

        // Walk each object-type folder in order and add @@ references
        List<String> allFolders = new ArrayList<>();
        for (OracleObjectType objType : OracleObjectType.values()) {
            allFolders.add(objType.folder);
        }
        allFolders.add("13_grants");

        for (String folder : allFolders) {
            Path folderPath = outputRoot.resolve(folder);
            if (!Files.exists(folderPath)) continue;

            List<Path> scripts;
            try (var stream = Files.list(folderPath)) {
                scripts = stream
                        .filter(p -> p.toString().endsWith(".sql"))
                        .sorted()
                        .collect(Collectors.toList());
            }

            if (scripts.isEmpty()) continue;

            String label = folder.substring(3).replace("_", " ").toUpperCase();
            sb.append(String.format("""
                    
                    PROMPT ----------------------------------------------------------
                    PROMPT   %s
                    PROMPT ----------------------------------------------------------
                    """, label));

            for (Path script : scripts) {
                sb.append("@@").append(folder).append("/").append(script.getFileName()).append("\n");
            }
        }

        // Post-deployment: recompile invalid objects
        sb.append(String.format("""
                
                
                PROMPT ----------------------------------------------------------
                PROMPT   POST-DEPLOYMENT: Recompiling Invalid Objects
                PROMPT ----------------------------------------------------------
                
                BEGIN
                  DBMS_UTILITY.COMPILE_SCHEMA(schema => '%s', compile_all => FALSE);
                END;
                /
                
                -- Validation: List any remaining invalid objects
                PROMPT
                PROMPT   Invalid objects after deployment:
                SELECT object_type, object_name, status
                FROM   all_objects
                WHERE  owner = '%s'
                AND    status = 'INVALID'
                ORDER  BY object_type, object_name;
                
                PROMPT
                PROMPT ============================================================
                PROMPT   DEPLOYMENT COMPLETE
                PROMPT ============================================================
                
                SPOOL OFF
                SET ECHO OFF
                SET TIMING OFF
                """, targetSchema, targetSchema));

        Path masterPath = outputRoot.resolve("master_run.sql");
        Files.writeString(masterPath, sb.toString());
        System.out.println("\n[OK]     master_run.sql generated.");
    }

    // ========================================================================
    // Summary
    // ========================================================================
    private void printSummary() {
        System.out.println();
        System.out.println("==========================================================");
        System.out.println("  GENERATION COMPLETE");
        System.out.println("==========================================================");
        System.out.printf("  Schema     : %s%n", targetSchema);
        System.out.printf("  Output     : %s%n", outputRoot.toAbsolutePath());
        System.out.printf("  Total      : %d object(s)%n", totalObjects);
        System.out.println("  ----------------------------------------------------------");
        for (var entry : stats.entrySet()) {
            System.out.printf("  %-25s : %d%n", entry.getKey().oracleType, entry.getValue());
        }
        System.out.println("  ----------------------------------------------------------");
        System.out.println("  Run with:  sqlplus user/pass@db @master_run.sql");
        System.out.println("==========================================================");
    }

    // ========================================================================
    // DDL extraction per object type
    // ========================================================================
    private void extractObjectType(Connection conn, OracleObjectType objType) throws Exception {
        String lookupSql = buildObjectLookupSql(objType);
        List<String> objectNames = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(lookupSql)) {
            ps.setString(1, targetSchema);
            // Default query has 2 bind vars (owner + object_type)
            if (needsTypeBind(objType)) {
                ps.setString(2, objType.oracleType);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    objectNames.add(rs.getString(1));
                }
            }
        }

        if (objectNames.isEmpty()) {
            System.out.printf("[SKIP]   %-25s -- no objects found%n", objType.oracleType);
            return;
        }

        System.out.printf("[EXPORT] %-25s -- %d object(s)%n", objType.oracleType, objectNames.size());
        int count = 0;

        for (String objName : objectNames) {
            String ddl = getDdl(conn, objType, objName);
            if (ddl == null || ddl.isBlank()) {
                System.out.printf("  [WARN] Could not extract DDL for %s.%s -- skipped%n",
                        targetSchema, objName);
                continue;
            }

            String script = buildScript(objType, objName, ddl);
            String filename = objName.toLowerCase().replaceAll("[^a-z0-9_$#]", "_") + ".sql";
            Path filePath = outputRoot.resolve(objType.folder).resolve(filename);
            Files.writeString(filePath, script);
            count++;
        }

        stats.put(objType, count);
        totalObjects += count;
    }

    private boolean needsTypeBind(OracleObjectType objType) {
        return switch (objType) {
            case INDEX, SYNONYM, DB_LINK, MVIEW, TABLE -> false;
            default -> true;
        };
    }
}
