import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.transition.TransitionValues
import android.transition.Visibility
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup

class CircularRevealTransition : Visibility() {
    override fun onAppear(
        sceneRoot: ViewGroup?,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        val startRadius = 70
        val endRadius = Math.hypot(view.width.toDouble(), view.height.toDouble()).toInt()
        val reveal = ViewAnimationUtils.createCircularReveal(
            view,
            view.width / 2,
            view.height / 2,
            startRadius.toFloat(),
            endRadius.toFloat()
        )
        //make view invisible until animation actually starts
        view.alpha = 0f
        reveal.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                view.alpha = 1f
            }
        })
        return reveal
    }

    override fun onDisappear(
        sceneRoot: ViewGroup?,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        val endRadius = 70
        val startRadius =
            Math.hypot(view.width.toDouble(), view.height.toDouble()).toInt()
        return ViewAnimationUtils.createCircularReveal(
            view,
            view.width / 2,
            view.height / 2,
            startRadius.toFloat(),
            endRadius.toFloat()
        )
    }
}
class VideoFadeTransition : Visibility() {

    override fun onAppear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        return ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            duration = 600
        }
    }

    override fun onDisappear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        return ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f).apply {
            duration = 600
        }
    }
}