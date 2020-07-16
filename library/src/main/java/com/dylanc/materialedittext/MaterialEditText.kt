package com.dylanc.materialedittext

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.BounceInterpolator
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import kotlin.math.log

class MaterialEditText(context: Context, attrs: AttributeSet) :
  AppCompatEditText(context, attrs), View.OnFocusChangeListener {

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val path = Path()

  private val textMargin = 8.dp
  private val smallTextSize = 12.dp
  private val smallTextBaseLine = 22.dp
  private var smallTextColor = Color.parseColor("#aaaaaa")
  private var lineColor = Color.parseColor("#cccccc")
  private var lineAccentColor = 0
  private val lineMaxShakeOffset = 15.dp
  private val lineOffset = 12.dp
  private var state = State.NORMAL
  private var initialInputType: Int

  private lateinit var highlightAnimator: ObjectAnimator
  private lateinit var pullLineAnimator: ObjectAnimator
  private lateinit var textFloatAnimator: ObjectAnimator
  private lateinit var focusedAnimatorSet: AnimatorSet
  private lateinit var riseAnimatorSet: AnimatorSet
  private lateinit var loseFocusAnimatorSet: AnimatorSet

  private val nonInputFilter = InputFilter { _, _, _, _, _, _ -> "" }

  private var textRiseFraction = 0f
    set(value) {
      field = value
      invalidate()
    }

  private var linePullDegree = 0f
    set(value) {
      field = value
      invalidate()
    }
  private var lineShakeOffset = lineMaxShakeOffset * 2
    set(value) {
      field = value
      invalidate()
    }
  private var showLineFraction = 0f
    set(value) {
      field = value
      invalidate()
    }

  init {
    paint.strokeJoin = Paint.Join.ROUND
    setHintTextColor(Color.TRANSPARENT)
    setBackgroundColor(Color.TRANSPARENT)
    setPadding(
      (paddingLeft + textMargin).toInt(),
      (paddingTop + textMargin + smallTextSize).toInt(),
      (paddingRight + textMargin).toInt(),
      (paddingBottom + lineMaxShakeOffset).toInt()
    )
    lineAccentColor = context.accentColor
    onFocusChangeListener = this
    initialInputType = inputType
    initAnimator()
  }

  private fun initAnimator() {
    highlightAnimator = ObjectAnimator.ofFloat(this, "showLineFraction", 1f)
      .apply {
        duration = 500
        state = State.SHOW_HIGHLIGHT
      }
    pullLineAnimator = ObjectAnimator.ofFloat(this, "linePullDegree", lineMaxShakeOffset * 2)
      .apply {
        state = State.PULL_LINE
      }
    textFloatAnimator = ObjectAnimator.ofFloat(this, "textRiseFraction", 1f)
      .apply {
        state = State.TEXT_RISE
      }
    val lineShakeAnimator =
      ObjectAnimator.ofFloat(
        this, "lineShakeOffset",
        lineMaxShakeOffset * 2, (-20).dp, 0f, 8.dp, 0.dp
      ).apply { duration = 400 }
    riseAnimatorSet = animatorSetOf {
      playTogether(textFloatAnimator, lineShakeAnimator)
    }
    focusedAnimatorSet = animatorSetOf {
      playSequentially(highlightAnimator, pullLineAnimator, riseAnimatorSet)
    }
    loseFocusAnimatorSet = animatorSetOf {
      playSequentially(highlightAnimator, textFloatAnimator)
      addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator?) {
          super.onAnimationEnd(animation)
          this@MaterialEditText.state = State.NORMAL
        }
      })
    }
  }

  override fun onFocusChange(v: View?, hasFocus: Boolean) {
    if (hasFocus) {
      setEditable(true)
      when (state) {
        State.NORMAL -> {
          if (!TextUtils.isEmpty(text)) {
            highlightAnimator.setFloatValues(0f, 1f)
            highlightAnimator.state = State.SHOW_HIGHLIGHT
            highlightAnimator.nextState = State.SELECTED
            highlightAnimator.doOnEnd {
              linePullDegree = 0f
            }
            highlightAnimator.start()
          } else {
            highlightAnimator.setFloatValues(0f, 1f)
            highlightAnimator.state = State.SHOW_HIGHLIGHT
            textFloatAnimator.setFloatValues(0f, 1f)
            textFloatAnimator.duration = 300
            textFloatAnimator.interpolator = null
            textFloatAnimator.state = State.TEXT_RISE
            setEditable(false)
            focusedAnimatorSet.listeners?.clear()
            focusedAnimatorSet.addListener(object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator?) {
                super.onAnimationEnd(animation)
                linePullDegree = 0f
                state = State.SELECTED
                setEditable(true)
                showKeyboard()
              }
            })
            focusedAnimatorSet.start()
          }
        }
        State.HIDE_HIGHLIGHT -> {
          loseFocusAnimatorSet.end()
          textRiseFraction = 1f
          highlightAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
              linePullDegree = 0f
              state = State.SELECTED
            }
          })
          highlightAnimator.reverse()
        }
        State.TEXT_DROP -> {
          loseFocusAnimatorSet.cancel()
          AnimatorSet().apply {
            textFloatAnimator.setFloatValues(textFloatAnimator.animatedValue as Float, 1f)
            highlightAnimator.setFloatValues(1f, 0f)
            playSequentially(textFloatAnimator, highlightAnimator)
            addListener(object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator?) {
                linePullDegree = 0f
                this@MaterialEditText.state = State.SELECTED
              }
            })
          }.start()
        }
        else -> {
        }
      }

    } else {
      when (state) {
        State.SHOW_HIGHLIGHT -> {
          stopFocusedAnimation()
          highlightAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
              state = State.NORMAL
            }
          })
          highlightAnimator.reverse()
        }
        State.PULL_LINE -> {
          stopFocusedAnimation()
          AnimatorSet().apply {
            pullLineAnimator.setFloatValues(linePullDegree, 0f)
            highlightAnimator.setFloatValues(1f, 0f)
            playSequentially(pullLineAnimator, highlightAnimator)
            addListener(object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator?) {
                this@MaterialEditText.state = State.NORMAL
              }
            })
          }.start()
        }
        State.TEXT_RISE -> {
          textFloatAnimator.setFloatValues(textRiseFraction, 0f)
          state = State.TEXT_DROP
//          textFloatAnimator.duration = 600
//          textFloatAnimator.interpolator = BounceInterpolator()
          focusedAnimatorSet.listeners?.clear()
          focusedAnimatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
              textFloatAnimator.interpolator = null
              state = State.NORMAL
              linePullDegree = 0f
            }
          })
        }
        State.SELECTED -> {
          if (!text.isNullOrBlank()) {
            highlightAnimator.setFloatValues(0f, 1f)
            highlightAnimator.state = State.HIDE_HIGHLIGHT
            highlightAnimator.nextState = State.NORMAL
            highlightAnimator.start()
          } else {
            highlightAnimator.setFloatValues(0f, 1f)
            highlightAnimator.state = State.HIDE_HIGHLIGHT
            textFloatAnimator.setFloatValues(1f, 0f)
            textFloatAnimator.state = State.TEXT_DROP
            loseFocusAnimatorSet.start()
          }
        }
        else -> {
        }
      }
    }
  }

  private fun stopFocusedAnimation() {
    focusedAnimatorSet.listeners?.clear()
    focusedAnimatorSet.cancel()
    textRiseFraction = if (text.isNullOrBlank()) 0f else 1f
  }

  override fun onDraw(canvas: Canvas?) {
    super.onDraw(canvas)
    if (canvas == null) {
      return
    }

    // 绘制 hint 文字
    paint.color = smallTextColor
    paint.style = Paint.Style.FILL
    paint.textSize = textSize - textRiseFraction * (textSize - smallTextSize)
    val textControlPotionY = when (state) {
      State.TEXT_RISE -> baseline + lineMaxShakeOffset * (1 - textRiseFraction)
      State.TEXT_DROP -> baseline + lineMaxShakeOffset * textRiseFraction
      State.PULL_LINE -> baseline + linePullDegree
      else -> baseline.toFloat()
    }
    val x2 = (width - paddingRight).toFloat()
    val vOffset = textRiseFraction * (smallTextBaseLine - baseline)
//    val vOffset = when (state) {
//      State.TEXT_DROP -> (1 - textRiseFraction) * (smallTextBaseLine - baseline)
//      else -> textRiseFraction * (smallTextBaseLine - baseline)
//    }
    path.reset()
    path.moveTo(textMargin, baseline.toFloat())
    path.quadTo(width / 2f, textControlPotionY, x2, baseline.toFloat())
    canvas.drawTextOnPath(hint.toString(), path, 0f, vOffset, paint)

    // 绘制抖动线
    paint.strokeWidth = 1.dp
    paint.style = Paint.Style.STROKE
    paint.color = when (state) {
      State.PULL_LINE,
      State.TEXT_RISE,
      State.SELECTED -> lineAccentColor
      else -> lineColor
    }
    val lineY = baseline + lineOffset
    val lineControlPointY = when (state) {
      State.TEXT_RISE -> lineY + lineShakeOffset
      State.PULL_LINE -> lineY + linePullDegree
      else -> lineY
    }
    path.reset()
    path.moveTo(textMargin, lineY)
    path.quadTo(width / 2f, lineControlPointY, (width - paddingRight).toFloat(), lineY)
    canvas.drawPath(path, paint)

    // 绘制高亮线
    val lineLength = width - textMargin - paddingRight
    paint.color = lineAccentColor
    when (state) {
      State.SHOW_HIGHLIGHT -> {
        canvas.drawLine(
          width / 2 - lineLength * showLineFraction / 2, lineY,
          width / 2 + lineLength * showLineFraction / 2, lineY, paint
        )
      }
      State.HIDE_HIGHLIGHT -> {
        canvas.drawLine(
          textMargin + lineLength * showLineFraction, lineY,
          width - paddingRight.toFloat(), lineY, paint
        )
      }
      else -> {
      }
    }
  }

  private fun setEditable(editable: Boolean) {
    if (editable) {
      inputType = initialInputType
      filters = arrayOf()
    } else {
      inputType = InputType.TYPE_NULL
      filters = arrayOf(nonInputFilter)
    }
  }

  private fun animatorSetOf(block: AnimatorSet.() -> Unit) =
    AnimatorSet().apply(block)

  private var Animator.state: State
    @Deprecated("Property does not have a getter", level = DeprecationLevel.ERROR)
    get() = throw NotImplementedError()
    set(value) {
      listeners?.clear()
      doOnStart {
        this@MaterialEditText.state = value
      }
    }

  private var Animator.nextState: State
    @Deprecated("Property does not have a getter", level = DeprecationLevel.ERROR)
    get() = throw NotImplementedError()
    set(value) {
      doOnEnd {
        this@MaterialEditText.state = value
      }
    }

  enum class State {
    NORMAL, SHOW_HIGHLIGHT, PULL_LINE, TEXT_RISE, SELECTED, HIDE_HIGHLIGHT, TEXT_DROP
  }

}
