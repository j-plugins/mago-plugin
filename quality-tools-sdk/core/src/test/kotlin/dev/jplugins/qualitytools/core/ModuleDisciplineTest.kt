package dev.jplugins.qualitytools.core

import org.junit.Test

/**
 * Smoke test for SDK rule 3: `:core` declares **zero** runtime
 * dependency on `com.intellij.*`, `com.jetbrains.php.*`, `org.jdom.*`,
 * or AWT/Swing.
 *
 * Mechanism: ask the JVM class-loader for canonical classes from each
 * forbidden namespace; assert each lookup throws
 * `ClassNotFoundException`. If a future build accidentally adds one
 * of those dependencies, this test fails immediately at red-build
 * time on the test classpath.
 *
 * Note: this is a runtime classpath test, not a static analysis. It
 * catches transitive deps via gradle as well as direct imports.
 */
class ModuleDisciplineTest {

    @Test
    fun `core has no IntelliJ platform class on classpath`() {
        assertAbsent("com.intellij.openapi.project.Project")
        assertAbsent("com.intellij.openapi.diagnostic.Logger")
        assertAbsent("com.intellij.openapi.vfs.VirtualFile")
    }

    @Test
    fun `core has no PHP plugin class on classpath`() {
        assertAbsent("com.jetbrains.php.tools.quality.QualityToolType")
        assertAbsent("com.jetbrains.php.config.interpreters.PhpInterpreter")
    }

    @Test
    fun `core has no JDOM class on classpath`() {
        assertAbsent("org.jdom.Element")
        assertAbsent("org.jdom.Document")
    }

    @Test
    fun `core has no Swing UI Designer class on classpath`() {
        assertAbsent("com.intellij.uiDesigner.core.GridConstraints")
        assertAbsent("com.intellij.uiDesigner.core.GridLayoutManager")
    }

    private fun assertAbsent(fqn: String) {
        try {
            Class.forName(fqn)
            kotlin.test.fail(
                "Forbidden class `$fqn` is on the :core test classpath. " +
                    "Something (probably a build dependency or a transitive) " +
                    "pulled an IntelliJ-platform / JDOM / Swing artifact " +
                    "into :core, violating SDK rule 3 (see docs/phases/README.md)."
            )
        } catch (_: ClassNotFoundException) {
            // expected
        } catch (_: NoClassDefFoundError) {
            // also acceptable — the class isn't loadable
        }
    }
}
