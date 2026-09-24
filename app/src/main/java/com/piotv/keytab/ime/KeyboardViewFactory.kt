package com.piotv.keytab.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Handler
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.piotv.keytab.R
import java.util.concurrent.Executor

/**
 * Baut den kompletten Keyboard-View auf (Phase 6, docs/REFACTORING_PLAN.md):
 * Themed-Context, Theme-Anwendung, Panel-Instanzen, Input-Router,
 * Wortvorhersage-Manager und Tasten-Skaler.
 *
 * Der [KeyTabImeService] behält nur das Delegieren/Wiring; die Factory kennt
 * den Service nicht, sondern nur die schmale [Deps]-Schnittstelle.
 */
class KeyboardViewFactory(private val deps: Deps) {

    /** Vom Service bereitgestellte Abhängigkeiten (schmale Schnittstelle). */
    interface Deps {
        /** Basis-Kontext des IME (Prefs/Resources – NICHT der themed Context). */
        val imeContext: Context
        fun isDarkMode(): Boolean
        /** keytab_config.txt im externen Files-Dir. */
        fun configFile(): java.io.File
        fun commitText(text: String)
        fun commitToApp(text: String)
        val isInputViewShown: Boolean
        /** Harte Sicherheitsregel: Passwort-/Sensibel-Feld? ([TrailLogic.isPersonalizedProcessingAllowed]). */
        fun isPersonalizedProcessingAllowed(): Boolean
        fun currentInputConnection(): InputConnection?
        fun sendKeyEvents(keyCode: Int)
        fun selectEditor()
        val ioExecutor: Executor
        val mainHandler: Handler
        val suggestionViews: Array<TextView?>
        val baseLetters: MutableMap<Button, Char>
    }

    /** Ergebnis eines View-Aufbaus; der Service übernimmt die Felder daraus. */
    data class Result(
        val root: View,
        val fileManagerPanel: FileManagerPanel,
        val editorPanel: EditorPanel,
        val terminalPanel: TerminalPanel,
        val clipboardPanel: ClipboardPanel,
        val snippetPanel: SnippetPanel,
        val router: InputRouter,
        val predictionManager: WordPredictionManager,
        val keyScaler: DynamicKeyScaler,
        val language: KeyboardLanguage
    )

    fun create(): Result {
        val ctx = deps.imeContext
        // Dark/Light-Override (Persistiert in SharedPreferences, Default = System)
        val conf = Configuration(ctx.resources.configuration)
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (deps.isDarkMode()) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val cfgCtx = ctx.createConfigurationContext(conf)
        val themedContext = ContextThemeWrapper(cfgCtx, R.style.Theme_KeyTab)
        val inflater = themedContext.getSystemService(android.view.LayoutInflater::class.java)
            ?: android.view.LayoutInflater.from(ctx)
        val root = inflater.cloneInContext(themedContext)
            .inflate(R.layout.keyboard_view, android.widget.FrameLayout(themedContext), false)
        // Konfiguration laden (Skalierung, Verschiebung, Seiten-Hinweise)
        val config = KeyTabConfig.load(deps.configFile())
        KeyScaleLogic.params = KeyScaleLogic.Params(
            maxScale = config.maxScale,
            midScale = config.midScale,
            hotThreshold = config.hotThreshold,
            midThreshold = config.midThreshold,
            minNeighborScale = config.minNeighborScale,
            midNeighborScale = config.midNeighborScale
        )
        // Theme-Umschalter-Icon passend zum aktiven Modus – monochrom (☾ Dark / ☀ Light),
        // kräftige Schrift in key_text (sw) und kleiner als zuvor
        val themeBtn = root.findViewById<Button>(R.id.key_theme)
        themeBtn?.text = if (deps.isDarkMode()) ThemeController.MOON_SYMBOL else ThemeController.SUN_SYMBOL
        // Farbe über den THEMA-übersteuerten Kontext (cfgCtx) auflösen, NICHT baseContext:
        // sonst gilt die System-Night-Farbe (weiß) trotz Light-Override → weiß auf weiß
        themeBtn?.setTextColor(ContextCompat.getColor(cfgCtx, R.color.key_text))
        themeBtn?.typeface = Typeface.DEFAULT_BOLD
        themeBtn?.textSize = 14f
        // Theme-Verlauf + Farb-Overrides (aus den Theme-Einstellungen) anwenden
        ThemeApplier.apply(
            com.piotv.keytab.Prefs.of(ctx),
            deps.isDarkMode(), root, cfgCtx)
        // Obere Ecken runden (12dp) — muss nach ThemeApplier sein
        KeyAnimations.applyRoundedCorners(root)
        // Optionale Zahlenreihe aus den Einstellungen
        root.findViewById<View>(R.id.num_row)?.visibility =
            if (com.piotv.keytab.Prefs.of(ctx)
                    .getBoolean(com.piotv.keytab.Prefs.KEY_NUM_ROW, false)) View.VISIBLE else View.GONE
        val fileManager = FileManagerPanel(ctx, root, deps.ioExecutor, deps.mainHandler) { deps.commitText(it) }
        val editor = EditorPanel(ctx, root, deps.ioExecutor, deps.mainHandler) { deps.commitToApp(it) }
        val terminal = TerminalPanel(ctx, root, deps.mainHandler)
        val snippets = SnippetPanel(
            ctx,
            deps.ioExecutor,
            deps.mainHandler,
            onEdit = {
                // Der Tab-Wechsel muss vor dem asynchronen Editor-Laden erfolgen.
                deps.selectEditor()
                editor.openFile(it)
            },
            onCommit = { deps.commitText(it) }
        )
        val clipboard = ClipboardPanel(ctx, deps.ioExecutor, deps.mainHandler,
            onCommit = { deps.commitToApp(it) },
            canAutoCapture = { deps.isInputViewShown },
            onAddToSnippet = { snippets.addFromClipboard(it) })
        // Eingabe-Routing (Phase 2): Ziele App/Editor/Terminal hinter einem Router;
        // die editorActive/terminalActive-Verzweigungsketten entfallen damit.
        val router = InputRouter(
            AppInputTarget(
                connection = { deps.currentInputConnection() },
                sendKey = { deps.sendKeyEvents(it) }),
            EditorInputTarget(editor),
            TerminalInputTarget(terminal))
        // Aktive Sprache aus den Einstellungen übernehmen (wirkt beim nächsten Öffnen)
        val language = com.piotv.keytab.MainActivity.activeLanguage(ctx)
        val predictionManager = WordPredictionManager(
            ctx, deps.ioExecutor, deps.mainHandler, deps.suggestionViews,
            inputOps = object : WordPredictionManager.InputOperations {
                override fun deleteBefore(count: Int) = router.deleteBefore(count)
                override fun deleteBeforeKeys(count: Int) = router.deleteBeforeKeys(count)
                override fun textBefore(count: Int): String = router.textBefore(count)
                override fun insert(text: String) = router.insert(text)
                override fun commitToApp(text: String) = deps.commitToApp(text)
            },
            personalizedProcessingAllowed = { deps.isPersonalizedProcessingAllowed() }
        )
        val keyScaler = DynamicKeyScaler(deps.baseLetters)
        // Generelles Tasten-Animationssystem (v0.9.7): LayoutTransition auf
        // dem abc-Container — jede Platzänderung (Extra-Keys-Zeile ein/aus,
        // Nummernreihe, Panels) animiert automatisch; vorhandene Tasten
        // rutschen weich in ihre neue Position.
        root.findViewById<ViewGroup?>(R.id.kb_panel)?.let { KeyAnimations.applyLayoutTransition(it) }
        // Skalierung darf über das Raster hinausragen: Clipping der gesamten
        // View-Hierarchie deaktivieren (sonst werden vergrößerte Tasten an den
        // Container-Grenzen abgeschnitten).
        disableClipping(root)
        return Result(root, fileManager, editor, terminal, clipboard, snippets,
            router, predictionManager, keyScaler, language)
    }

    private fun disableClipping(v: View?) {
        var cur: View? = v
        while (cur != null) {
            if (cur is ViewGroup) {
                cur.clipChildren = false
                cur.clipToPadding = false
            }
            cur = cur.parent as? View
        }
    }
}
