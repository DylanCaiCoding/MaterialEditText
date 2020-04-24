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
import android.view.View
import android.view.animation.BounceInterpolator
import androidx.appcompat.widget.AppCompatEditText

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
  private var expandState = ExpandState.NORMAL
  private var realInputType: Int

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
    realInputType = inputType
    initAnimator()
  }

  private fun initAnimator() {
    highlightAnimator = ObjectAnimator.ofFloat(this, "showLineFraction", 1f)
      .apply {
        duration = 500
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationStart(animation: Animator?) {
            expandState = ExpandState.SHOW_HIGHLIGHT
          }
        })
      }
    pullLineAnimator = ObjectAnimator.ofFloat(this, "linePullDegree", lineMaxShakeOffset * 2)
      .apply {
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationStart(animation: Animator?) {
            expandState = ExpandState.PULL_LINE
          }
        })
      }
    textFloatAnimator = ObjectAnimator.ofFloat(this, "textRiseFraction", 1f)
      .apply {
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationStart(animation: Animator?) {
            expandState = ExpandState.TEXT_RISE
          }
        })
      }
    val lineShakeAnimator =
      ObjectAnimator.ofFloat(
        this, "lineShakeOffset",
        lineMaxShakeOffset * 2, (-20).dp, 0f, 8.dp, 0.dp
      ).apply { duration = 400 }
    riseAnimatorSet = AnimatorSet()
      .apply {
        playTogether(textFloatAnimator, lineShakeAnimator)
      }
    focusedAnimatorSet = AnimatorSet()
      .apply {
        playSequentially(highlightAnimator, pullLineAnimator, riseAnimatorSet)
      }
    loseFocusAnimatorSet = AnimatorSet()
      .apply {
        playSequentially(highlightAnimator, textFloatAnimator)
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationEnd(animation: Animator?) {
            super.onAnimationEnd(animation)
            expandState = ExpandState.NORMAL
          }
        })
      }
  }

  override fun onFocusChange(v: View?, hasFocus: Boolean) {
    if (hasFocus) {
      setEditable(true)
      when (expandState) {
        ExpandState.NORMAL -> {
          if (!TextUtils.isEmpty(text)) {
            highlightAnimator.setFloatValues(0f, 1f)
            highlightAnimator.listeners?.clear()
            highlightAnimator.addListener(object : AnimatorListenerAdapter() {
              override fun onAnimationStart(animation: Animator?) {
                expandState = ExpandState.SHOW_HIGHLIGHT
              }

              override fun onAnimationEnd(animation: Animator?) {
                linePullDegree = 0f
                expandState = ExpandState.EXPANDED
              }
            })
            highlightAnimator.start()
          } else {
            setShowHighlightAnimator()
            setTextRiseAnimator()
            setEditable(false)
            focusedAnimatorSet.listeners?.clear()
            focusedAnimatorSet.addListener(object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator?) {
                super.onAnimationEnd(animation)
                linePullDegree = 0f
                expandState = ExpandState.EXPANDED
                setEditable(true)
                showKeyboard()
              }
            })
            focusedAnimatorSet.start()
          }
        }
        ExpandState.HIDE_HIGHLIGHT -> {
          loseFocusAnimatorSet.end()
          textRiseFraction = 1f
          highlightAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
              linePullDegree = 0f
              expandState = ExpandState.EXPANDED
            }
          })
          highlightAnimator.reverse()
        }
        ExpandState.TEXT_DROP -> {
          loseFocusAnimatorSet.end()
          textRiseFraction = textRiseFraction
          AnimatorSet().apply {
            textFloatAnimator.setFloatValues(textRiseFraction, 1f)
            highlightAnimator.setFloatValues(1f, 0f)
            playSequentially(textFloatAnimator, highlightAnimator)
            addListener(object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator?) {
                linePullDegree = 0f
                expandState = ExpandState.EXPANDED
              }
            })
          }.start()
        }
        else -> {
        }
      }

    } else {
      when (expandState) {
        ExpandState.SHOW_HIGHLIGHT -> {
          stopFocusedAnimation()
          highlightAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
              expandState = ExpandState.NORMAL
            }
          })
          highlightAnimator.reverse()
        }
        ExpandState.PULL_LINE -> {
          stopFocusedAnimation()
          AnimatorSet().apply {
            pullLineAnimator.setFloatValues(linePullDegree, 0f)
            highlightAnimator.setFloatValues(1f, 0f)
            playSequentially(pullLineAnimator, highlightAnimator)
            addListener(object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator?) {
                expandState = ExpandState.NORMAL
              }
            })
          }.start()
        }
        ExpandState.TEXT_RISE -> {
          textFloatAnimator.setFloatValues(0f, 1f, 0f)
          textFloatAnimator.duration = 600
          textFloatAnimator.interpolator = BounceInterpolator()
          focusedAnimatorSet.listeners?.clear()
          focusedAnimatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
              textFloatAnimator.interpolator = null
              expandState = ExpandState.NORMAL
              linePullDegree = 0f
            }
          })
        }
        ExpandState.EXPANDED -> {
          if (!TextUtils.isEmpty(text)) {
            highlightAnimator.setFloatValues(0f, 1f)
            highlightAnimator.listeners?.clear()
            highlightAnimator.addListener(object : AnimatorListenerAdapter() {
              override fun onAnimationStart(animation: Animator?) {
                expandState = ExpandState.HIDE_HIGHLIGHT
              }

              override fun onAnimationEnd(animation: Animator?) {
                expandState = ExpandState.NORMAL
              }
            })
            highlightAnimator.start()
          } else {
            setHideHighlightAnimator()
            setTextDropAnimator()
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
    textRiseFraction = if (TextUtils.isEmpty(text)) {
      0f
    } else {
      1f
    }
  }

  private fun setShowHighlightAnimator() {
    highlightAnimator.setFloatValues(0f, 1f)
    highlightAnimator.listeners?.clear()
    highlightAnimator.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationStart(animation: Animator?) {
        expandState = ExpandState.SHOW_HIGHLIGHT
      }
    })
  }

  private fun setHideHighlightAnimator() {
    highlightAnimator.setFloatValues(0f, 1f)
    highlightAnimator.listeners?.clear()
    highlightAnimator.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationStart(animation: Animator?) {
        expandState = ExpandState.HIDE_HIGHLIGHT
      }
    })
  }

  private fun setTextRiseAnimator() {
    textFloatAnimator.setFloatValues(0f, 1f)
    textFloatAnimator.duration = 300
    textFloatAnimator.listeners?.clear()
    textFloatAnimator.interpolator = null
    textFloatAnimator.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationStart(animation: Animator?) {
        expandState = ExpandState.TEXT_RISE
      }
    })
  }

  private fun setTextDropAnimator() {
    textFloatAnimator.setFloatValues(1f, 0f)
    textFloatAnimator.listeners?.clear()
    textFloatAnimator.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationStart(animation: Animator?) {
        expandState = ExpandState.TEXT_DROP
      }
    })
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
    val textControlPotionY = when (expandState) {
      ExpandState.TEXT_RISE -> baseline + linePullDegree * (1 - textRiseFraction)
      ExpandState.PULL_LINE-> baseline + linePullDegree
      else -> baseline.toFloat()
    }
    val x2 = (width - paddingRight).toFloat()
    val vOffset = textRiseFraction * (smallTextBaseLine - baseline)
    path.reset()
    path.moveTo(textMargin, baseline.toFloat())
    path.quadTo(width / 2f, textControlPotionY, x2, baseline.toFloat())
    canvas.drawTextOnPath(hint.toString(), path, 0f, vOffset, paint)

    // 绘制抖动线
    paint.strokeWidth = 1.dp
    paint.style = Paint.Style.STROKE
    paint.color = when (expandState) {
      ExpandState.PULL_LINE,
      ExpandState.TEXT_RISE,
      ExpandState.EXPANDED -> lineAccentColor
      else -> lineColor
    }
    val lineY = baseline + lineOffset
    val lineControlPointY = when (expandState) {
      ExpandState.TEXT_RISE -> lineY + lineShakeOffset
      else -> lineY + linePullDegree
    }
    path.reset()
    path.moveTo(textMargin, lineY)
    path.quadTo(width / 2f, lineControlPointY, (width - paddingRight).toFloat(), lineY)
    canvas.drawPath(path, paint)

    // 绘制高亮线
    val lineLength = width - textMargin - paddingRight
    paint.color = lineAccentColor
    when (expandState) {
      ExpandState.SHOW_HIGHLIGHT -> {
        canvas.drawLine(
          width / 2 - lineLength * showLineFraction / 2, lineY,
          width / 2 + lineLength * showLineFraction / 2, lineY, paint
        )
      }
      ExpandState.HIDE_HIGHLIGHT -> {
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
      inputType = realInputType
      filters = arrayOf()
    } else {
      inputType = InputType.TYPE_NULL
      filters = arrayOf(nonInputFilter)
    }
  }

  override fun setInputType(type: Int) {
    super.setInputType(type)
  }

}

enum class ExpandState {
  NORMAL, SHOW_HIGHLIGHT, PULL_LINE, TEXT_RISE, EXPANDED, HIDE_HIGHLIGHT, TEXT_DROP
}