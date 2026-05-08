/**
 * Client Smoke Test annotation API.
 *
 * <p>The annotation uses {@link java.lang.annotation.RetentionPolicy#CLASS} retention so
 * that Forge's {@code ModFileScanData} can discover annotated classes via ASM bytecode
 * scanning without triggering JVM class initialization.</p>
 */
@org.jspecify.annotations.NullMarked
package io.github.tt432.clientsmokeannotation;
