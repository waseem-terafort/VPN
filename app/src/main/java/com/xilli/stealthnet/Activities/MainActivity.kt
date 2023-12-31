package com.xilli.stealthnet.Activities

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender.SendIntentException
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.NavHostFragment
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.SkuType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.bumptech.glide.Glide
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.tasks.OnSuccessListener
import com.xilli.stealthnet.Config
import com.xilli.stealthnet.Fragments.HomeFragment
import android.Manifest
import com.xilli.stealthnet.R
import com.xilli.stealthnet.Utils.ActiveServer
import com.xilli.stealthnet.helper.OnVpnConnectedListener
import com.xilli.stealthnet.helper.Utils
import com.xilli.stealthnet.helper.Utils.flagName
import com.xilli.stealthnet.helper.Utils.imgFlag
import com.xilli.stealthnet.helper.Utils.isConnected
import com.xilli.stealthnet.helper.Utils.isVpnConnected
import com.xilli.stealthnet.helper.Utils.textDownloading
import com.xilli.stealthnet.helper.Utils.textUploading
import com.xilli.stealthnet.helper.Utils.timerTextView
import com.xilli.stealthnet.helper.Utils.updateUI
import com.xilli.stealthnet.model.Countries
import com.xilli.stealthnet.Fragments.viewmodels.SharedViewmodel
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.EasyPermissions
import top.oneconnectapi.app.OpenVpnApi
import top.oneconnectapi.app.core.OpenVPNThread
import java.util.Arrays
import java.util.Locale
import java.util.Objects


class MainActivity : AppCompatActivity(),  EasyPermissions.PermissionCallbacks {
    private var isFirst = true
    private var billingClient: BillingClient? = null
    private val skusWithSkuDetails: MutableMap<String, SkuDetails> = HashMap()
    private val allSubs: List<String> = ArrayList(
        Arrays.asList(
            Config.all_month_id,
            Config.all_threemonths_id,
            Config.all_sixmonths_id,
            Config.all_yearly_id
        )
    )
    val premiumServers: List<Countries> by lazy { Utils.loadServersvip() }
    var hasNavigated = false
    private val viewModel by viewModels<SharedViewmodel>()
    private var STATUS: String? = "DISCONNECTED"
    private var yourFragment: HomeFragment? = null
    private var vpnStateCallback: OnVpnConnectedListener? = null

    override fun onStart() {
        super.onStart()

        // Check if the user made a selection
        if (viewModel.selectedItem.value == null) {
            // Automatically select the first premium server
            if (premiumServers.isNotEmpty()) {
                val firstPremiumCountry = premiumServers[0] // Automatically select the first premium server
                viewModel.selectedItem.value = firstPremiumCountry
            }
        }
        viewModel.selectedItem.observe(this) { selectedItem ->
            // Initialize selectedCountry with the value from viewModel
            selectedCountry = selectedItem
            // Update UI as needed
            updateUI("LOAD")
        }

        // Rest of your onStart logic
        val intent = intent
        if (intent.extras != null) {
            val selectedCountryFromIntent = intent.extras?.getParcelable<Countries>("c")
            if (selectedCountryFromIntent != null) {
                selectedCountry = selectedCountryFromIntent
                updateUI("LOAD")
                if (!Utility.isOnline(applicationContext)) {
                    showMessage("No Internet Connection", "error")
                } else {
                    showMessage("working", "success")
                }
            }
        } else if (selectedCountry != null) {
            updateUI("CONNECTED")
            imgFlag?.let {
                Glide.with(this)
                    .load(selectedCountry?.flagUrl)
                    .into(it)
            }
            flagName?.text = selectedCountry?.country
        }
    }


    val isVpnActiveFlow = callbackFlow {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            channel.close(IllegalStateException("connectivity manager is null"))
            return@callbackFlow
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    channel.trySend(true)
                }

                override fun onLost(network: Network) {
                    channel.trySend(false)
                }
            }
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(),
                callback
            )
            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val sharedPreferences = getSharedPreferences("onboarding", MODE_PRIVATE)
        val onboardingCompleted = sharedPreferences.getBoolean("completed", false)

        if (!onboardingCompleted) {
            navController.navigate(R.id.onboardingScreenFragment)
        } else {
            lifecycleScope.launch {
                isVpnActiveFlow.collect { vpnActive ->
                    if (!hasNavigated && vpnActive) {
                        hasNavigated = true
                        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                        val navController = navHostFragment.navController
                        navController.navigate(R.id.rateScreenFragment)
                    }
                }
            }
        }
        val countryName: String? = intent.getStringExtra("countryName")
        val flagUrl: String? = intent.getStringExtra("flagUrl")
        Utils.countryName = countryName
        Utils.flagUrl = flagUrl
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 11) {
            Toast.makeText(this, "Start Downloand", Toast.LENGTH_SHORT).show()
            if (resultCode != RESULT_OK) {
                Log.d("Update", "Update failed$resultCode")
            }
        }
        if (resultCode == RESULT_OK) {
            startVpn()
        } else {
            showMessage("Permission Denied", "error")
        }
    }

    fun prepareVpn(): Boolean {
        imgFlag?.let {
            Glide.with(this)
                .load(selectedCountry?.flagUrl)
                .into(it)
        }
        flagName?.text = selectedCountry?.country
        if (Utility.isOnline(applicationContext)) {
            if (selectedCountry != null) {
                val intent = VpnService.prepare(this)
                Log.v("CHECKSTATE", "start")
                if (intent != null) {
                    startActivityForResult(intent, 1)
                } else startVpn()
            } else {
                showMessage("Please select a server first", "")
            }
        } else {
            showMessage("No Internet Connection", "error")
        }
        return isVpnConnected(this)
    }


    fun startVpn() {
        try {
            if (selectedCountry != null) {
                ActiveServer.saveServer(selectedCountry, this@MainActivity)
                OpenVpnApi.startVpn(
                    this,
                    selectedCountry?.ovpn,
                    selectedCountry?.country,
                    selectedCountry?.ovpnUserName,
                    selectedCountry?.ovpnUserPassword
                )

            } else {
                Toast.makeText(this, "No country selected", Toast.LENGTH_SHORT).show()
            }
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter("your.vpn.state.action")
        registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(broadcastReceiver)
    }

    private var broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {

                val vpnState = intent.getStringExtra("state")
                if (vpnState != null) {
                    if (vpnState == "CONNECTED") {
                        // VPN is connected
                        updateUI("connected") // You can update UI accordingly
                        isConnected = true

                    } else if (vpnState == "DISCONNECTED") {
                        // VPN is disconnected
                        updateUI("disconnected") // You can update UI accordingly
                        isConnected = false
                    }
                    Log.v("CHECKSTATE1", vpnState)
                }

                Objects.requireNonNull(getIntent().getStringExtra("state")).let {
                    if (it != null) {
                        updateUI(it)

                    }
                }
                Objects.requireNonNull(intent.getStringExtra("state"))
                    .let {
                        if (it != null) {
                            Log.v("CHECKSTATE", it)
                        }
                    }
                if (isFirst) {
                    if (ActiveServer.getSavedServer(this@MainActivity).country != null) {
                        selectedCountry = ActiveServer.getSavedServer(this@MainActivity)
                        imgFlag?.let {
                            Glide.with(this@MainActivity)
                                .load(selectedCountry?.flagUrl)
                                .into(it)
                        }
                        flagName?.text = selectedCountry?.country
                    }
                    isFirst = false
                }
                isConnected = true

            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                var duration = intent.getStringExtra("duration")
                var lastPacketReceive = intent.getStringExtra("lastPacketReceive")
                var byteIn = intent.getStringExtra("byteIn")
                var byteOut = intent.getStringExtra("byteOut")
                if (duration == null) duration = "00:00:00"
                if (lastPacketReceive == null) lastPacketReceive = "0"
                if (byteIn == null) byteIn = " "
                if (byteOut == null) byteOut = " "
                updateConnectionStatus(duration, lastPacketReceive, byteIn, byteOut)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun checkSelectedCountry() {
        if (selectedCountry == null) {
            updateUI("DISCONNECT")
            showMessage("Please select a server first", "")
        } else {
            prepareVpn()
            updateUI("LOAD")
        }
    }

    companion object {

        var selectedCountry: Countries? = null

        @JvmField
        var type: String? = ""

    }


    fun btnConnectDisconnect() {
        if (Utils.STATUS != "DISCONNECTED") {
            showMessage("Wait", "success")
        } else {
            if (!Utility.isOnline(applicationContext)) {
                showMessage("No Internet Connection", "error")
            } else {
                checkSelectedCountry()
            }
        }
    }

    fun showMessage(msg: String?, type: String) {

        if (type == "success") {
            Toasty.success(
                this,
                msg + "",
                Toast.LENGTH_SHORT
            ).show()
        } else if (type == "error") {
            Toasty.error(
                this,
                msg + "",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toasty.normal(
                this,
                msg + "",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun updateConnectionStatus(
        duration: String?,
        lastPacketReceive: String?,
        byteIn: String,
        byteOut: String
    ) {
        val byteinKb = byteIn.split("-").toTypedArray()[1]
        val byteoutKb = byteOut.split("-").toTypedArray()[1]

        textDownloading?.text = byteinKb
        textUploading?.text = byteoutKb
        timerTextView?.text = duration
    }
    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        TODO("Not yet implemented")
    }
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        TODO("Not yet implemented")
    }
}