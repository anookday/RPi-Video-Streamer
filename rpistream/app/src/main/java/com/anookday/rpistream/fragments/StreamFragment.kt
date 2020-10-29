package com.anookday.rpistream.fragments

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import android.transition.ChangeBounds
import android.view.SurfaceHolder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.anookday.rpistream.*
import com.anookday.rpistream.chat.TwitchChatAdapter
import com.anookday.rpistream.chat.TwitchChatItem
import com.anookday.rpistream.databinding.FragmentStreamBinding
import com.anookday.rpistream.stream.StreamService
import kotlinx.android.synthetic.main.fab_toggle_off.*
import kotlinx.android.synthetic.main.fragment_stream.*
import timber.log.Timber

class StreamFragment : Fragment() {
    private lateinit var binding: FragmentStreamBinding
    private lateinit var chatAdapter: TwitchChatAdapter
    private val viewModel: MainViewModel by activityViewModels()

    // fab animation
    private lateinit var fabConstraintOn: ConstraintSet
    private lateinit var fabConstraintOff: ConstraintSet
    private lateinit var fabTransition: ChangeBounds
    private var isMenuPressed = false

    /**
     * Record audio request permission launcher
     */
    private val audioRequestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                viewModel.toggleAudio()
            } else {
                showMessage("audioRequestPermissionLauncher: Record audio permission denied")
            }
        }

    /**
     * SurfaceView callback object
     */
    private var surfaceViewCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Timber.v("surfaceViewCallback: Surface created")
        }

        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            Timber.v("surfaceViewCallback: Surface changed")
            if (width == 0 || height == 0) return
            if (StreamService.isPreview) {
                viewModel.startPreview(width, height)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            Timber.v("surfaceViewCallback: Surface destroyed")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        chatAdapter = TwitchChatAdapter()
        fabConstraintOn = ConstraintSet().apply { clone(context, R.layout.fab_toggle_on) }
        fabConstraintOff = ConstraintSet().apply { clone(context, R.layout.fab_toggle_off) }
        fabTransition = ChangeBounds().apply { interpolator = OvershootInterpolator(1.0F) }

        binding = FragmentStreamBinding.inflate(inflater, container, false).apply {
            chatMessages.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(context)
                adapter = chatAdapter
            }
            cameraPreview.holder.addCallback(surfaceViewCallback)
        }

        viewModel.apply {
            init(requireContext(), binding.cameraPreview)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        menu_fab.setOnClickListener(::onMenuFabClick)
        video_fab.setOnClickListener(::onVideoFabClick)
        audio_fab.setOnClickListener(::onAudioFabClick)
        stream_fab.setOnClickListener(::onStreamFabClick)

        viewModel.apply {
            usbStatus.observe(viewLifecycleOwner, ::onUsbStatusChange)
            connectStatus.observe(viewLifecycleOwner, ::onConnectStatusChange)
            authStatus.observe(viewLifecycleOwner, ::onAuthStatusChange)
            videoStatus.observe(viewLifecycleOwner, ::onVideoStatusChange)
            audioStatus.observe(viewLifecycleOwner, ::onAudioStatusChange)
            chatMessages.observe(viewLifecycleOwner, ::onChatMessagesChange)
            connectToChat()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        viewModel.setCurrentFragment(CurrentFragmentName.STREAM)
    }

    override fun onResume() {
        super.onResume()
        (activity as MainActivity).enableHeaderAndDrawer()
    }

    /**
     * Displays given message in a toast.
     *
     * @param msg Text to display
     */
    private fun showMessage(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    private fun onMenuFabClick(view: View) {
        isMenuPressed = !isMenuPressed
        var alpha = 0F
        TransitionManager.beginDelayedTransition(fab_container, fabTransition)
        if (isMenuPressed) {
            alpha = 0.75F
            fabConstraintOn.applyTo(fab_container)
        } else {
            fabConstraintOff.applyTo(fab_container)
        }
        ObjectAnimator.ofFloat(main_dimmed_bg, "alpha", alpha).apply{
            duration = 300
            start()
        }
    }

    private fun onVideoFabClick(view: View) {
        viewModel.toggleVideo()
    }

    private fun onAudioFabClick(view: View) {
        when (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> {
                viewModel.toggleAudio()
            }
            else -> {
                audioRequestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun onStreamFabClick(view: View) {
        viewModel.toggleStream()
    }

    private fun onUsbStatusChange(status: UsbConnectStatus?) {
        status?.let {
            when (it) {
                UsbConnectStatus.ATTACHED -> showMessage("USB device attached")
                UsbConnectStatus.DETACHED -> showMessage("USB device detached")
            }
        }
    }

    private fun onConnectStatusChange(status: RtmpConnectStatus?) {
        status?.let {
            when (it) {
                RtmpConnectStatus.SUCCESS -> {
                    stream_fab_text.text = getText(R.string.stream_on_text)
                    stream_fab.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.colorAccent)
                    )
                    showMessage("Connection success")
                }
                RtmpConnectStatus.FAIL -> showMessage("Connection failed")
                RtmpConnectStatus.DISCONNECT -> {
                    stream_fab_text.text = getText(R.string.stream_off_text)
                    stream_fab.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.colorPrimary)
                    )
                    showMessage("Disconnected")
                }
            }
        }
    }

    private fun onAuthStatusChange(status: RtmpAuthStatus?) {
        status?.let {
            when (it) {
                RtmpAuthStatus.SUCCESS -> showMessage("Auth success")
                RtmpAuthStatus.FAIL -> showMessage("Auth error")
            }
        }
    }

    private fun onVideoStatusChange(status: String?) {
        if (status != null) {
            video_fab.setImageResource(R.drawable.ic_baseline_videocam_24)
            video_fab.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.colorAccent)
            )
            video_fab_text.text = status
        } else {
            video_fab.setImageResource(R.drawable.ic_baseline_videocam_off_24)
            video_fab.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            )
            video_fab_text.text = getText(R.string.video_off_text)
        }
    }

    private fun onAudioStatusChange(status: String?) {
        if (status != null) {
            audio_fab.setImageResource(R.drawable.ic_baseline_mic_24)
            audio_fab.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.colorAccent)
            )
            audio_fab_text.text = status
        } else {
            audio_fab.setImageResource(R.drawable.ic_baseline_mic_off_24)
            audio_fab.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            )
            audio_fab_text.text = getString(R.string.audio_off_text)
        }
    }

    private fun onChatMessagesChange(messages: MutableList<TwitchChatItem>) {
        chatAdapter.submitList(messages.toList())
    }
}