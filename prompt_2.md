Act as an expert legacy systems migration architect and create a comprehensive `claude-skills` configuration profile specifically for analyzing, refactoring, and modernizing a legacy Oracle Pro*C/C++ codebase.

The skill set must incorporate the following operational workflows:

1. **preflight**: Validate codebase structure, identify Pro*C file types (`.pc`, `.h`, `.c`), and detect missing dependencies, build files, or include paths.
2. **assess**: Evaluate code complexity, technical debt, lines of code (LOC), and reliance on deprecated C standards or proprietary Oracle Pro*C precompiler options.
3. **map**: Trace file dependencies, function call graphs, and architectural topology across modules.
4. **extract-rules**: Extract and catalog all embedded SQL statements, cursors, host variables, and data access logic.
5. **extract-business-rules**: Decouple domain logic from database operations and document core business processes.
6. **database-dependency-analysis**: Deep dive into `EXEC SQL` blocks, transaction management boundaries (commits, rollbacks, savepoints), and dynamic SQL usage. *(Added)*
7. **error-handling-audit**: Analyze `EXEC SQL WHENEVER` clauses, `SQLCA`, and `SQLCODE` error-handling mechanisms for legacy fragility and silent failures. *(Added)*
8. **security-vulnerability-scan**: Identify classic C/C++ vulnerabilities (buffer overflows, unsafe string operations, memory leaks) and SQL injection vectors in dynamic Pro*C queries. *(Added)*
9. **generate-mermaid-diagrams**: Produce structural flowcharts, data-flow diagrams, and system call-graphs in Mermaid format for visual documentation.
10. **brief**: Generate concise executive summaries and technical handoff documentation.
11. **plan-modernisation**: Outline a phased refactoring and migration roadmap, targeting transition paths to modern C++, containerization, or decoupled microservices with modern ORMs.

Structure the output as a fully functional, ready-to-use `claude-skills` definition format including system instructions, persona definitions, command triggers, and structured markdown output templates for each workflow phase.
