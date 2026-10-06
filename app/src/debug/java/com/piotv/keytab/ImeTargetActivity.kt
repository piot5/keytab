package com.piotv.keytab

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Foreign target window for real InputMethodService instrumentation tests.
 *
 * Lives in the **debug** variant, not in androidTest, and that is deliberate:
 * an Activity declared in the test APK runs in the *test* process
 * (com.piotv.keytab.debug.test), while the instrumentation runs in the app
 * process (com.piotv.keytab.debug). MonitoringInstrumentation.startActivitySync
 * refuses that split with "Intent in process com.piotv.keytab.debug resolved to
 * different process com.piotv.keytab.debug.test", so ActivityTestRule could
 * never hand the object to the test. Declared here it runs in the app process
 * and a typed reference works again. Release builds are unaffected: src/debug
 * is not part of them.
 */
class ImeTargetActivity : Activity() {
    lateinit var normalField: EditText
        private set
    lateinit var passwordField: EditText
        private set
    lateinit var noLearningField: EditText
        private set
    val receivedKeyCodes: MutableList<Int> = mutableListOf()

    private lateinit var testNameView: TextView
    private lateinit var expectationView: TextView
    private lateinit var statusView: TextView
    private lateinit var pauseButton: Button
    private lateinit var stepButton: Button
    private lateinit var playButton: Button
    private lateinit var notesField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        normalField = createField("normal")
        passwordField = createField("password", InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_PASSWORD)
        noLearningField = createField("no-learning", InputType.TYPE_CLASS_TEXT).apply {
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(buildRunnerPanel())
            addView(normalField)
            addView(passwordField)
            addView(noLearningField)
        }
        setContentView(ScrollView(this).apply { addView(content) })

        TestRunnerState.onChange = { runOnUiThread { refreshRunner() } }
        refreshRunner()
        // Der Fokus gehört dem Testfeld: das Notizfeld liegt im Baum davor und
        // würde sonst beim Start die Input-Session an sich binden.
        normalField.requestFocus()
    }

    override fun onDestroy() {
        TestRunnerState.onChange = null
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) receivedKeyCodes += event.keyCode
        return super.dispatchKeyEvent(event)
    }

    // ---------- Test-Runner-Oberfläche ----------

    private companion object {
        const val RUNNER_TITLE_SP = 14f
        const val RUNNER_TEXT_SP = 12f
        const val RUNNER_PAD_H_DP = 8
        const val RUNNER_PAD_V_DP = 6
    }

    private fun buildRunnerPanel(): View {
        testNameView = TextView(this).apply {
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, RUNNER_TITLE_SP)
        }
        expectationView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, RUNNER_TEXT_SP)
            setTextColor(Color.DKGRAY)
        }
        statusView = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, RUNNER_TEXT_SP) }
        pauseButton = runnerButton("⏸ Pause") { TestRunnerState.pause() }
        stepButton = runnerButton("⏭ Schritt") { TestRunnerState.step() }
        playButton = runnerButton("▶ Weiter") { TestRunnerState.resume() }
        notesField = EditText(this).apply {
            hint = "Abweichung beschreiben (wird mit dem Test protokolliert)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, RUNNER_TEXT_SP)
            setMaxLines(3)
            setSingleLine(false)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) =
                    TestRunnerState.setNotes(s?.toString().orEmpty())
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFF3CD"))
            setPadding(dp(RUNNER_PAD_H_DP), dp(RUNNER_PAD_V_DP), dp(RUNNER_PAD_H_DP), dp(RUNNER_PAD_V_DP))
            addView(testNameView)
            addView(expectationView)
            addView(LinearLayout(this@ImeTargetActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(statusView)
                addView(pauseButton)
                addView(stepButton)
                addView(playButton)
            })
            addView(notesField)
        }
    }

    private fun runnerButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, RUNNER_TEXT_SP)
            setOnClickListener { action() }
        }

    /** Anzeige aus [TestRunnerState] nachziehen (läuft auf dem UI-Thread). */
    private fun refreshRunner() {
        val paused = TestRunnerState.paused
        testNameView.text = if (TestRunnerState.currentTest.isEmpty()) {
            "Test-Runner — wartet auf den ersten Test"
        } else {
            "▶ ${TestRunnerState.currentTest}"
        }
        expectationView.text = TestRunnerState.expectation
        statusView.text = if (paused) "PAUSIERT  " else "läuft  "
        pauseButton.isEnabled = !paused
        stepButton.isEnabled = paused
        playButton.isEnabled = paused
        // Das Notizfeld ist ein EditText: sichtbar ist es im Accessibility-Baum
        // und würde von den Tests als "erstes EditText" getroffen (die Tests
        // tippen dann in die Notiz statt ins Testfeld) — und es schiebt die
        // Testfelder aus dem Sichtbereich über der Tastatur. Deshalb nur im
        // pausierten Zustand einblenden; genau dann wird es gebraucht.
        notesField.visibility = if (paused) View.VISIBLE else View.GONE
        val notes = TestRunnerState.notes
        if (notesField.text?.toString().orEmpty() != notes) notesField.setText(notes)
    }

    private fun dp(value: Int): Int = TypedValue
        .applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics)
        .toInt()

    // ---------- Test-Host ----------

    private fun createField(label: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(this).apply {
            hint = label
            setSingleLine(false)
            this.inputType = inputType
        }
}
