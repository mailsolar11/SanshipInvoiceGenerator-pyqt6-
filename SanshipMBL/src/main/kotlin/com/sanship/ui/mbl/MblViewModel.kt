package com.sanship.ui.mbl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sanship.data.MblData
import com.sanship.data.CargoItem
import com.sanship.data.ClientRepository
import com.sanship.data.ClientMaster
import com.sanship.data.DatabaseManager
import com.sanship.utils.PdfGenerator
import com.sanship.utils.PrintStationeryGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.awt.Desktop

enum class AddressFieldType { CONSIGNOR, CONSIGNEE, NOTIFY, AGENT }
enum class PortFieldType { RECEIPT, LOADING, DISCHARGE, DELIVERY }

class MblViewModel(private val clientRepository: ClientRepository) {

    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    var mblData by mutableStateOf(MblData())
        private set

    // --- SEARCH STATES ---
    var blSearchQuery by mutableStateOf("")
    var blSearchResults by mutableStateOf(emptyList<String>())
    var isBlSearchExpanded by mutableStateOf(false)

    // --- CLIENT DROPDOWN STATE ---
    var clientList by mutableStateOf(emptyList<ClientMaster>())
    var filteredClientList by mutableStateOf(emptyList<ClientMaster>())
    var isClientDropdownOpen by mutableStateOf(false)
    var activeAddressField by mutableStateOf(AddressFieldType.CONSIGNOR)

    // Add Client Dialog
    var showAddClientDialog by mutableStateOf(false)
    var newClientShortName by mutableStateOf("")
    var newClientFullName by mutableStateOf("")
    var newClientAddress by mutableStateOf("")

    // --- PORT SEARCH STATE ---
    var portSearchQuery by mutableStateOf("")
    var portSearchResults by mutableStateOf(emptyList<String>())
    var isPortSearchExpanded by mutableStateOf(false)
    var activePortField by mutableStateOf(PortFieldType.LOADING)

    // --- EXPANDED WORLDWIDE PORT LIST ---
    private val masterPortList = listOf(
        // INDIA
        "Nhava Sheva, India", "Mundra, India", "Chennai, India", "Kolkata, India",
        "Visakhapatnam, India", "Cochin, India", "Tuticorin, India", "Pipavav, India",
        "Hazira, India", "Krishnapatnam, India", "Kattupalli, India", "Haldia, India",

        // MIDDLE EAST
        "Jebel Ali, UAE", "Dubai, UAE", "Abu Dhabi, UAE", "Sharjah, UAE",
        "Dammam, Saudi Arabia", "Jeddah, Saudi Arabia", "King Abdullah Port, Saudi Arabia",
        "Riyadh Dry Port, Saudi Arabia", "Sohar, Oman", "Salalah, Oman",
        "Hamad, Qatar", "Doha, Qatar", "Shuwaikh, Kuwait", "Shuaiba, Kuwait",
        "Bahrain (Khalifa Bin Salman), Bahrain", "Umm Qasr, Iraq", "Bandar Abbas, Iran",

        // ASIA (SE & EAST)
        "Singapore", "Port Klang, Malaysia", "Tanjung Pelepas, Malaysia", "Penang, Malaysia",
        "Shanghai, China", "Ningbo-Zhoushan, China", "Shenzhen, China", "Guangzhou, China",
        "Qingdao, China", "Tianjin, China", "Xiamen, China", "Dalian, China", "Hong Kong",
        "Busan, South Korea", "Incheon, South Korea", "Tokyo, Japan", "Yokohama, Japan",
        "Kobe, Japan", "Osaka, Japan", "Nagoya, Japan",
        "Ho Chi Minh (Cat Lai), Vietnam", "Haiphong, Vietnam", "Da Nang, Vietnam",
        "Laem Chabang, Thailand", "Bangkok, Thailand", "Jakarta, Indonesia",
        "Surabaya, Indonesia", "Belawan, Indonesia", "Manila, Philippines",
        "Kaohsiung, Taiwan", "Keelung, Taiwan", "Taichung, Taiwan",

        // SOUTH ASIA
        "Colombo, Sri Lanka", "Chittagong, Bangladesh", "Mongla, Bangladesh",
        "Karachi, Pakistan", "Port Qasim, Pakistan",

        // EUROPE
        "Rotterdam, Netherlands", "Antwerp, Belgium", "Hamburg, Germany",
        "Bremerhaven, Germany", "Wilhelmshaven, Germany",
        "Felixstowe, UK", "London Gateway, UK", "Southampton, UK", "Liverpool, UK",
        "Le Havre, France", "Marseille, France",
        "Algeciras, Spain", "Valencia, Spain", "Barcelona, Spain",
        "Genoa, Italy", "Gioia Tauro, Italy", "La Spezia, Italy", "Trieste, Italy",
        "Piraeus, Greece", "Thessaloniki, Greece", "Ambarli (Istanbul), Turkey",
        "Mersin, Turkey", "Izmir, Turkey", "Gdansk, Poland", "Koper, Slovenia",

        // NORTH AMERICA
        "Los Angeles, USA", "Long Beach, USA", "New York/New Jersey, USA",
        "Savannah, USA", "Houston, USA", "Norfolk, USA", "Charleston, USA",
        "Seattle, USA", "Tacoma, USA", "Oakland, USA", "Miami, USA",
        "Vancouver, Canada", "Prince Rupert, Canada", "Montreal, Canada", "Toronto, Canada",
        "Halifax, Canada",

        // LATIN AMERICA
        "Santos, Brazil", "Paranagua, Brazil", "Rio de Janeiro, Brazil",
        "Manzanillo, Mexico", "Veracruz, Mexico", "Lazaro Cardenas, Mexico",
        "Colon, Panama", "Balboa, Panama", "Cartagena, Colombia", "Buenaventura, Colombia",
        "Callao, Peru", "San Antonio, Chile", "Valparaiso, Chile",
        "Buenos Aires, Argentina", "Montevideo, Uruguay",

        // AFRICA
        "Durban, South Africa", "Cape Town, South Africa", "Coega (Ngqura), South Africa",
        "Mombasa, Kenya", "Dar es Salaam, Tanzania", "Beira, Mozambique",
        "Lagos (Apapa/Tin Can), Nigeria", "Tema, Ghana", "Abidjan, Ivory Coast",
        "Dakar, Senegal", "Tanger Med, Morocco", "Casablanca, Morocco",
        "Port Said, Egypt", "Alexandria, Egypt", "Damietta, Egypt",
        "Djibouti", "Port Sudan, Sudan",

        // OCEANIA
        "Melbourne, Australia", "Sydney, Australia", "Brisbane, Australia",
        "Fremantle, Australia", "Adelaide, Australia",
        "Auckland, New Zealand", "Tauranga, New Zealand", "Lyttelton, New Zealand"
    ).sorted()

    init {
        refreshClientList()
    }

    private fun refreshClientList() {
        viewModelScope.launch {
            clientRepository.getAllClients().collect { clients ->
                clientList = clients
                filteredClientList = clients
            }
        }
    }

    // --- CLIENT DROPDOWN LOGIC ---
    fun openClientDropdown(field: AddressFieldType) {
        activeAddressField = field
        filteredClientList = clientList
        isClientDropdownOpen = true
    }

    fun onClientQueryChanged(query: String) {
        if (query.isBlank()) {
            filteredClientList = clientList
        } else {
            filteredClientList = clientList.filter {
                it.shortName.contains(query, ignoreCase = true) ||
                        it.fullName.contains(query, ignoreCase = true)
            }
        }
        isClientDropdownOpen = true
    }

    fun onClientSelected(client: ClientMaster) {
        updateAddressField(activeAddressField, client.fullAddress)
        isClientDropdownOpen = false
    }

    private fun updateAddressField(field: AddressFieldType, value: String) {
        mblData = when (field) {
            AddressFieldType.CONSIGNOR -> mblData.copy(consignor = value)
            AddressFieldType.CONSIGNEE -> mblData.copy(consignee = value)
            AddressFieldType.NOTIFY -> mblData.copy(notifyAddress = value)
            AddressFieldType.AGENT -> mblData.copy(deliveryAgent = value)
        }
    }

    // --- CLIENT ADD LOGIC ---
    fun saveNewClient() {
        if (newClientShortName.isNotBlank() && newClientAddress.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val newClient = ClientMaster(
                    shortName = newClientShortName,
                    fullName = newClientFullName,
                    fullAddress = newClientAddress
                )
                clientRepository.addClient(newClient)
                launch(Dispatchers.Main) {
                    newClientShortName = ""
                    newClientFullName = ""
                    newClientAddress = ""
                    showAddClientDialog = false
                    refreshClientList()
                }
            }
        }
    }

    // --- PDF GENERATION ---
    fun generateDigitalPdf() {
        if (mblData.mtdNumber.isNotBlank()) {
            DatabaseManager.saveBill(mblData)

            val safeFileName = "Digital_" + mblData.mtdNumber.replace("/", "_").replace("\\", "_") + ".pdf"
            val outputPath = com.sanship.utils.DocumentPaths.getMblPath(safeFileName)
            PdfGenerator.generatePdf(outputPath, mblData)
            val file = File(outputPath)

            try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun generatePrintOverlay() {
        if (mblData.mtdNumber.isNotBlank()) {
            DatabaseManager.saveBill(mblData)
            val safeFileName = "Overlay_" + mblData.mtdNumber.replace("/", "_").replace("\\", "_") + ".pdf"
            val outputPath = com.sanship.utils.DocumentPaths.getMblPath(safeFileName)
            PrintStationeryGenerator.generatePrintOverlay(outputPath, mblData)
            val file = File(outputPath)

            try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // --- BL SEARCH ---
    fun onBlSearchQueryChange(query: String) {
        blSearchQuery = query
        if (query.isNotEmpty()) {
            val allIds = DatabaseManager.getAllMtdNumbers()
            blSearchResults = allIds.filter { it.contains(query, ignoreCase = true) }
            isBlSearchExpanded = true
        } else {
            blSearchResults = emptyList()
            isBlSearchExpanded = false
        }
    }
    fun loadBill(mtdNumber: String) {
        val loadedData = DatabaseManager.getBill(mtdNumber)
        if (loadedData != null) {
            mblData = loadedData
            blSearchQuery = ""
            isBlSearchExpanded = false
        }
    }
    fun createSimilarBl() { mblData = mblData.createCopyForNewEntry() }

    // --- PORT SEARCH LOGIC ---
    fun setTargetPortField(field: PortFieldType) {
        activePortField = field
        portSearchQuery = when (field) {
            PortFieldType.RECEIPT -> mblData.placeReceipt
            PortFieldType.LOADING -> mblData.portLoading
            PortFieldType.DISCHARGE -> mblData.portDischarge
            PortFieldType.DELIVERY -> mblData.placeDelivery
        }
        portSearchResults = emptyList() // Don't show list until they type or click
        isPortSearchExpanded = false
    }

    fun onPortSearchQueryChange(query: String) {
        portSearchQuery = query
        updateActivePortField(query)
        if (query.isNotEmpty()) {
            portSearchResults = masterPortList.filter { it.contains(query, ignoreCase = true) }
            isPortSearchExpanded = portSearchResults.isNotEmpty()
        } else {
            portSearchResults = emptyList()
            isPortSearchExpanded = false
        }
    }

    fun onPortSelected(selectedPort: String) {
        portSearchQuery = selectedPort
        updateActivePortField(selectedPort)
        portSearchResults = emptyList()
        isPortSearchExpanded = false
    }

    private fun updateActivePortField(value: String) {
        mblData = when (activePortField) {
            PortFieldType.RECEIPT -> mblData.copy(placeReceipt = value)
            PortFieldType.LOADING -> mblData.copy(portLoading = value)
            PortFieldType.DISCHARGE -> mblData.copy(portDischarge = value)
            PortFieldType.DELIVERY -> mblData.copy(placeDelivery = value)
        }
    }

    // --- DATA UPDATERS ---
    fun updateConsignor(v: String) { mblData = mblData.copy(consignor = v) }
    fun updateConsignee(v: String) { mblData = mblData.copy(consignee = v) }
    fun updateNotify(v: String) { mblData = mblData.copy(notifyAddress = v) }
    fun updateMtd(v: String) { mblData = mblData.copy(mtdNumber = v) }
    fun updateRef(v: String) { mblData = mblData.copy(refNumber = v) }
    fun updatePreCarriage(v: String) { mblData = mblData.copy(preCarriage = v) }
    fun updateAgent(v: String) { mblData = mblData.copy(deliveryAgent = v) }
    fun updateVessel(v: String) { mblData = mblData.copy(vessel = v) }
    fun updateVoy(v: String) { mblData = mblData.copy(voyNumber = v) }
    fun updateMode(v: String) { mblData = mblData.copy(mode = v) }
    fun updateRoute(v: String) { mblData = mblData.copy(route = v) }
    fun updateMainCustoms(v: String) { mblData = mblData.copy(mainCustomsSeal = v) }
    fun updateMainAgent(v: String) { mblData = mblData.copy(mainAgentSeal = v) }
    fun updateMarksNumbers(v: String) { mblData = mblData.copy(marksNumbers = v) }
    fun updateGoodsDesc(v: String) { mblData = mblData.copy(goodsDescription = v) }
    fun addCargoRow() { mblData = mblData.copy(cargoItems = mblData.cargoItems + CargoItem()) }
    fun removeCargoRow(index: Int) {
        if (mblData.cargoItems.size > 1) {
            val list = mblData.cargoItems.toMutableList().apply { removeAt(index) }
            mblData = mblData.copy(cargoItems = list)
        }
    }
    fun updateCargoItem(index: Int, item: CargoItem) {
        val list = mblData.cargoItems.toMutableList()
        list[index] = item
        mblData = mblData.copy(cargoItems = list)
    }
    fun updateFreight(v: String) { mblData = mblData.copy(freightAmount = v) }
    fun updatePayable(v: String) { mblData = mblData.copy(freightPayableAt = v) }
    fun updateOriginals(v: String) { mblData = mblData.copy(originalMtds = v) }
    fun updatePlaceDate(v: String) { mblData = mblData.copy(placeDateIssue = v) }
    fun updateOthers(v: String) { mblData = mblData.copy(otherParticulars = v) }
}