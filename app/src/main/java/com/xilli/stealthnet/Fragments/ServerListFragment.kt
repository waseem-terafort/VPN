package com.xilli.stealthnet.Fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xilli.stealthnet.Activities.MainActivity
import com.xilli.stealthnet.AdapterWrappers.SearchView_Free_Adapter
import com.xilli.stealthnet.AdapterWrappers.SearchView_Premium_Adapter
import com.xilli.stealthnet.R
import com.xilli.stealthnet.Utils.Constants
import com.xilli.stealthnet.databinding.FragmentServerListBinding
import com.xilli.stealthnet.model.Countries
import com.xilli.stealthnet.ui.viewmodels.SharedViewmodel
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject


class ServerListFragment : Fragment(), SearchView_Premium_Adapter.OnItemClickListener {
    private var binding: FragmentServerListBinding?=null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapterPREMIUM: SearchView_Premium_Adapter
    private lateinit var adapterFREE: SearchView_Free_Adapter
    private var isBackgroundChanged = false
    private var selectedPosition = RecyclerView.NO_POSITION
    private var fragment: Fragment?= null
    val viewModel: SharedViewmodel by viewModels()
    val premiumServers: List<Countries> by lazy { loadServersvip() }
    val freeServers: List<Countries> by lazy { loadServers() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentServerListBinding.inflate(inflater, container, false)
//        selectedServer = DataItemPremium("Default Server", "10.0.0.5", R.drawable.flag, R.drawable.ic_signal, R.drawable.ic_green_crown)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFreeRecyclerView()
        setupPremiumRecyclerView()
        clicklistner()
        searchview()
        val sharedPrefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val selectedCountryName = sharedPrefs.getString("selectedCountryName", null)
        val selectedCountryFlagUrl = sharedPrefs.getString("selectedCountryFlagUrl", null)

        if (selectedCountryName != null && selectedCountryFlagUrl != null) {
            val selectedItem = Countries(selectedCountryName, selectedCountryFlagUrl, "", "", "")
            viewModel.selectedItem.value = selectedItem
        }

        binding?.constraintLayout2?.performClick()
    }

    private fun searchview() {
        binding?.searchEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                search(query)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun search(query: String) {
        val filteredPremiumServers = premiumServers.filter { server ->
            server.country?.contains(query, ignoreCase = true) == true
        }

        adapterPREMIUM.submitList(filteredPremiumServers)

        val filteredFreeServers = freeServers.filter { server ->
            server.country?.contains(query, ignoreCase = true) == true
        }

        adapterFREE.submitList(filteredFreeServers)
    }


    private fun setupPremiumRecyclerView() {
        recyclerView = binding?.recyclerView ?: return
        val premiumServers = loadServersvip()
        adapterPREMIUM = SearchView_Premium_Adapter(requireContext(),premiumServers )
        adapterPREMIUM.setOnItemClickListener(this)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapterPREMIUM
    }


    private fun setupFreeRecyclerView() {
        recyclerView = binding?.recyclerview2 ?: return
        val freeserver = loadServers()
        adapterFREE = SearchView_Free_Adapter(requireContext(), freeserver, this)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapterFREE
        adapterFREE.setOnItemClickListener { position ->
            adapterFREE.setSelectedPosition(position)
            adapterPREMIUM.resetSelection()
            adapterFREE.notifyDataSetChanged()
            adapterPREMIUM.notifyDataSetChanged()
//            binding?.constraintLayout2?.setBackgroundResource(R.drawable.background_black_card)
//            binding?.radio?.isChecked = false
        }
        loadServers()
    }
    private fun saveSelectedCountry(country: Countries?) {
        val sharedPrefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()

        if (country != null) {
            editor.putString("selectedCountryName", country.getCountry1())
            editor.putString("selectedCountryFlagUrl", country.getFlagUrl1())
        } else {
            // If no item is selected, clear the saved data
            editor.remove("selectedCountryName")
            editor.remove("selectedCountryFlagUrl")
        }

        editor.apply()
    }

    private fun clicklistner() {
        binding?.imageView7?.setOnClickListener {
            findNavController().popBackStack()
        }
        viewModel.selectedItem.observe(viewLifecycleOwner) { selectedItem ->
            saveSelectedCountry(selectedItem)
            selectedItem?.let { item ->
                binding?.flagName?.text = item.getCountry1()
                binding?.imageViewflag?.let {
                    Glide.with(requireContext())
                        .load(item.getFlagUrl1())
                        .into(it)
                }
            }
        }





        binding?.constraintLayout2?.setOnClickListener {
//            if (::adapterPREMIUM.isInitialized ){
//                adapterPREMIUM.resetSelection()
//                adapterPREMIUM.notifyDataSetChanged()
//            }

//            selectedPosition = RecyclerView.NO_POSITION
//            adapterFREE.resetSelection()
//            adapterFREE.notifyDataSetChanged()
//            isBackgroundChanged = !isBackgroundChanged

            binding?.radio?.isChecked = !binding?.radio?.isChecked!!
            binding?.constraintLayout2?.setBackgroundResource(R.drawable.selector_background)
        }

    }
    private fun loadServers(): List<Countries> {
        val servers = ArrayList<Countries>()
        try {
            val jsonArray = JSONArray(Constants.FREE_SERVERS)
            for (i in 0 until jsonArray.length()) {
                val `object` = jsonArray[i] as JSONObject
                servers.add(
                    Countries(
                        `object`.getString("serverName"),
                        `object`.getString("flag_url"),
                        `object`.getString("ovpnConfiguration"),
                        `object`.getString("vpnUserName"),
                        `object`.getString("vpnPassword")
                    )
                )
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return servers
    }
    private fun loadServersvip(): List<Countries> {
        val servers = ArrayList<Countries>()
        try {
            val jsonArray = JSONArray(Constants.PREMIUM_SERVERS)
            for (i in 0 until jsonArray.length()) {
                val `object` = jsonArray[i] as JSONObject
                servers.add(
                    Countries(
                        `object`.getString("serverName"),
                        `object`.getString("flag_url"),
                        `object`.getString("ovpnConfiguration"),
                        `object`.getString("vpnUserName"),
                        `object`.getString("vpnPassword")
                    )
                )
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return servers
    }

    override fun onItemClick(country: Countries, position: Int) {
//        if (Config.vip_subscription || Config.all_subscription) {
        viewModel.selectedItem.value = country
        val intent = Intent(context, MainActivity::class.java)
        intent.putExtra("c", country)
        intent.putExtra("type", MainActivity.type)
        intent.putExtra("countryName", country.getCountry1())
        intent.putExtra("flagUrl", country.getFlagUrl1())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context?.startActivity(intent)
//            } else {
//                unblockServer()
//            }


        adapterPREMIUM.setSelectedPosition(position)
        adapterFREE.resetSelection()
        adapterPREMIUM.notifyDataSetChanged()
        adapterFREE.notifyDataSetChanged()
//        binding?.constraintLayout2?.setBackgroundResource(R.drawable.background_black_card)

//        binding?.radio?.isChecked = false
    }
}