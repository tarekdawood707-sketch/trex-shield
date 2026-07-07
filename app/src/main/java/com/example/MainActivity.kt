package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

// =========================================================================
// 1. نماذج البيانات الأساسية لـ درع تركس (Core Trex Data Models)
// =========================================================================

enum class Suit { HEART, DIAMOND, CLUB, SPADE }

data class TrexCard(
    val id: Int,
    val value: Int, // 1 (A) إلى 13 (K) - الولد 11، البنت 12، الشيخ 13
    val suit: Suit,
    var ownerId: Int? = null,
    var isPlayed: Boolean = false
)

enum class LedgerType {
    SUCCESS,       // حركة صحيحة
    WARNING,       // كشف خطأ أو غش تم منعه
    SECURITY_BREACH, // محاولة تلاعب مأهولة
    SYSTEM         // رسالة من نظام التأمين
}

data class LedgerEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: String,
    val message: String,
    val type: LedgerType
)

// =========================================================================
// 2. محرك النزاهة والتحقق من القوانين (Integrity & Rules Engine)
// =========================================================================

class TrexViewModel : ViewModel() {
    // أسماء اللاعبين الأربعة
    var playerNames = mutableStateListOf("اللاعب 1", "اللاعب 2", "اللاعب 3", "اللاعب 4")
    
    // الأوراق الـ 52 الأساسية في اللعبة
    var cards = mutableStateOf<List<TrexCard>>(generateInitialDeck())
    
    // حالات التحكم بالجولة
    var currentTurnPlayerId by mutableStateOf(1)
    var startingPlayerId by mutableStateOf(1)
    var isGameStarted by mutableStateOf(false)
    
    // حاويات الأوراق السرية للاعبين أثناء الإعداد
    val playerHands = mutableStateMapOf<Int, List<TrexCard>>()
    
    // اللاعب الفعال لإدخال أوراقه سرياً
    var activeInputPlayerId by mutableStateOf<Int?>(null)
    
    // تنبيهات الحماية
    var alertMessage by mutableStateOf("")
    var isAlertActive by mutableStateOf(false)
    var alertIsError by mutableStateOf(true)
    
    // سجل النزاهة الرقمي اللحظي
    val ledger = mutableStateListOf<LedgerEntry>()
    
    // مؤشرات النزاهة والإحصاءات العامة
    var fraudAttempts = mutableStateOf(0)
    var totalPlayedCount = mutableStateOf(0)

    init {
        logEvent("تم إقلاع نظام درع تركس الرقمي. حامي اللعبة جاهز للعمل.", LedgerType.SYSTEM)
    }

    fun resetGame() {
        cards.value = generateInitialDeck()
        currentTurnPlayerId = startingPlayerId
        isGameStarted = false
        playerHands.clear()
        activeInputPlayerId = null
        alertMessage = ""
        isAlertActive = false
        ledger.clear()
        fraudAttempts.value = 0
        totalPlayedCount.value = 0
        logEvent("تم تصفير اللعبة وإعادة تهيئة الطاولة حماية للنزاهة.", LedgerType.SYSTEM)
    }

    fun logEvent(message: String, type: LedgerType) {
        val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val timeStr = formatter.format(java.util.Date())
        ledger.add(0, LedgerEntry(timestamp = timeStr, message = message, type = type))
    }

    private fun generateInitialDeck(): List<TrexCard> {
        val list = mutableListOf<TrexCard>()
        var id = 1
        for (suit in Suit.values()) {
            for (value in 1..13) {
                list.add(TrexCard(id++, value, suit))
            }
        }
        return list
    }

    // التحقق من صلاحية حركة ورقة معينة حسب قواعد تركس الإيجابية
    fun isValidMove(card: TrexCard): Boolean {
        val playedCards = cards.value.filter { it.isPlayed }
        val cardsOfSameSuit = playedCards.filter { it.suit == card.suit }
        
        // إذا كان اللون مغلقاً، فيجب البدء بالولد (11) حتماً
        if (cardsOfSameSuit.isEmpty()) {
            return card.value == 11
        }
        
        // إذا كان اللون مفتوحاً، يسمح بلعب الورقة الأعلى مباشرة أو الأقل مباشرة
        val currentMax = cardsOfSameSuit.maxOf { it.value }
        val currentMin = cardsOfSameSuit.minOf { it.value }
        
        return card.value == currentMax + 1 || card.value == currentMin - 1
    }

    // فحص صامت خلف الكواليس لتحديد ما إذا كان اللاعب يمتلك حركات قانونية متاحة
    fun hasAvailableMoves(playerId: Int): Boolean {
        val playerHand = cards.value.filter { it.ownerId == playerId && !it.isPlayed }
        for (card in playerHand) {
            if (isValidMove(card)) return true
        }
        return false
    }

    // تنفيذ محاولة لعب ورقة مع التحقق من النزاهة والملكية وقوانين تركس
    fun attemptPlayCard(cardId: Int): Boolean {
        val targetCard = cards.value.find { it.id == cardId } ?: return false
        val currentPlayerName = playerNames[currentTurnPlayerId - 1]

        if (targetCard.isPlayed) {
            triggerAlert("هذه الورقة تم لعبها سابقاً على الطاولة!", true)
            return false
        }

        // 1. فحص الملكية السرية للكشف عن اللعب بأوراق الخصم أو الغش
        if (targetCard.ownerId != currentTurnPlayerId) {
            fraudAttempts.value += 1
            logEvent("محاولة تلاعب: حاول $currentPlayerName لعب ورقة لا يملكها في يده السرية: [${getCardNameArabic(targetCard)}].", LedgerType.SECURITY_BREACH)
            triggerAlert("كشف تلاعب! الورقة [${getCardNameArabic(targetCard)}] لا تنتمي ليدك السرية المغلقة يا $currentPlayerName!", true)
            return false
        }

        // 2. فحص قانونية الحركة بناءً على حالة الأوراق الملعوبة بالكامل
        if (!isValidMove(targetCard)) {
            fraudAttempts.value += 1
            logEvent("حركة ممنوعة: حاول $currentPlayerName لعب كرت غير قانوني حالياً: [${getCardNameArabic(targetCard)}].", LedgerType.WARNING)
            triggerAlert("حركة غير قانونية! لا يمكن لعب الكرت [${getCardNameArabic(targetCard)}] الآن حسب تسلسل تركس الإيجابية.", true)
            return false
        }

        // تسجيل الحركة بنجاح تام وتدوينها
        targetCard.isPlayed = true
        totalPlayedCount.value += 1
        logEvent("حركة نزيهة: لعب $currentPlayerName الكرت [${getCardNameArabic(targetCard)}] بنجاح ومطابقة واقعية.", LedgerType.SUCCESS)
        triggerAlert("تم تسجيل ولعب [${getCardNameArabic(targetCard)}] لللاعب $currentPlayerName بنجاح.", false)
        
        // نقل الدور
        rotateTurn()
        return true
    }

    // تنفيذ التمرير (Pass) مع التحقق الأوتوماتيكي لمنع الكذب والتحايل
    fun attemptPass(): Boolean {
        val currentPlayerName = playerNames[currentTurnPlayerId - 1]
        
        // فحص ما إذا كان اللاعب يكذب ويملك ورقة قانونية صالحة للعب
        if (hasAvailableMoves(currentTurnPlayerId)) {
            fraudAttempts.value += 1
            logEvent("كشف كذب التمرير: حاول $currentPlayerName عمل (Pass) ولديه أوراق صالحة للعب برمجياً!", LedgerType.SECURITY_BREACH)
            triggerAlert("تنبيه نزاهة! ممنوع التمرير يا $currentPlayerName. النظام يعلم برمجياً أن لديك حركة واحدة صالحة للعب على الأقل في يدك السرية!", true)
            return false
        }

        logEvent("تمرير معتمد: قام $currentPlayerName بالتمرير لعدم وجود أي حركة قانونية متاحة.", LedgerType.SUCCESS)
        rotateTurn()
        return true
    }

    private fun rotateTurn() {
        currentTurnPlayerId = if (currentTurnPlayerId == 4) 1 else currentTurnPlayerId + 1
    }

    private fun triggerAlert(message: String, isError: Boolean) {
        alertMessage = message
        this.alertIsError = isError
        isAlertActive = true
    }

    fun getCardNameArabic(card: TrexCard): String {
        val valueStr = when (card.value) {
            1 -> "A"
            11 -> "ولد (J)"
            12 -> "بنت (Q)"
            13 -> "شيخ (K)"
            else -> card.value.toString()
        }
        val suitStr = when (card.suit) {
            Suit.HEART -> "كبة ♥"
            Suit.DIAMOND -> "ديناري ♦"
            Suit.CLUB -> "سباتي ♣"
            Suit.SPADE -> "سبيت ♠"
        }
        return "$valueStr $suitStr"
    }

    // تحديد المتاح للعب في لون معين لتوجيه اللاعبين
    fun getSuitPlayableBounds(suit: Suit): Pair<Int, Int>? {
        val playedCards = cards.value.filter { it.isPlayed && it.suit == suit }
        if (playedCards.isEmpty()) return null
        return Pair(playedCards.minOf { it.value }, playedCards.maxOf { it.value })
    }
}

// =========================================================================
// 3. النشاط الرئيسي وتوفير الواجهة العربية (MainActivity & RTL Support)
// =========================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // تفعيل واجهة الاستخدام من اليمين لليسار RTL لتقديم تجربة عربية فريدة
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                        color = Color(0xFF04100B) // لون طاولة الكازينو الداكن والفاخر
                    ) {
                        TrexAppNavigation()
                    }
                }
            }
        }
    }
}

@Composable
fun TrexAppNavigation() {
    val viewModel: TrexViewModel = viewModel()
    
    Crossfade(targetState = viewModel.isGameStarted, label = "ScreenTransition") { isStarted ->
        if (!isStarted) {
            SetupGameScreen(viewModel = viewModel)
        } else {
            MainGamePlayScreen(viewModel = viewModel)
        }
    }
}

// =========================================================================
// 4. شاشة الإعداد الآمنة وإدخال الأوراق الفردية السرية
// =========================================================================

@Composable
fun SetupGameScreen(viewModel: TrexViewModel) {
    var validationError by remember { mutableStateOf("") }
    
    if (viewModel.activeInputPlayerId != null) {
        // شاشة الإدخال السري المعماة لللاعب لمنع التجسس على كروته أثناء إدخالها
        SecretHandInputComponent(
            playerId = viewModel.activeInputPlayerId!!,
            playerName = viewModel.playerNames[viewModel.activeInputPlayerId!! - 1],
            onHandConfirmed = { selectedCards ->
                viewModel.playerHands[viewModel.activeInputPlayerId!!] = selectedCards
                viewModel.activeInputPlayerId = null
                validationError = ""
            },
            onCancel = {
                viewModel.activeInputPlayerId = null
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // صورة البانر الفاخرة للطاولة الممتدة
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner),
                        contentDescription = "طاولة لعب تركس",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "درع النزاهة لتركس الإيجابية",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFD4AF37), // ذهبي فاخر
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "حارس النزاهة الرقمي اللحظي لكشف الكذب والغش",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Text(
                text = "إعداد جولة اللعب المؤمنة",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "ادخل أسماء اللاعبين الأربعة واقفل الأوراق لإنشاء مصفوفة النزاهة السحابية للعبة.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // مدخلات أسماء اللاعبين ومقاطع الإدخال السرية
            viewModel.playerNames.forEachIndexed { index, name ->
                val playerId = index + 1
                val isHandEntered = viewModel.playerHands.containsKey(playerId)
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C241B)),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isHandEntered) Color(0xFF2E7D32) else Color(0xFFD4AF37).copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isHandEntered) Color(0xFF4CAF50) else Color(0xFFD4AF37)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        OutlinedTextField(
                            value = name,
                            onValueChange = { viewModel.playerNames[index] = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("اسم اللاعب $playerId", color = Color.White.copy(alpha = 0.6f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFD4AF37),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            ),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Button(
                            onClick = { viewModel.activeInputPlayerId = playerId },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isHandEntered) Color(0xFF2E7D32) else Color(0xFFD4AF37),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = if (isHandEntered) "تم القفل ✓" else "إدخال الكروت",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // تحديد اللاعب البادئ بالمملكة
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C241B)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "اللاعب البادئ باختيار المملكة:",
                        color = Color(0xFFD4AF37),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        viewModel.playerNames.forEachIndexed { index, name ->
                            val playerId = index + 1
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { viewModel.startingPlayerId = playerId }
                            ) {
                                RadioButton(
                                    selected = (viewModel.startingPlayerId == playerId),
                                    onClick = { viewModel.startingPlayerId = playerId },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD4AF37))
                                )
                                Text(
                                    text = name.take(6),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (validationError.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF621B1B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.Red)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = validationError, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val totalSelected = viewModel.playerHands.values.flatten()
                    val hasDuplicates = totalSelected.groupBy { it.id }.any { it.value.size > 1 }
                    
                    if (viewModel.playerHands.size < 4) {
                        validationError = "تأمين ناقص: يجب إدخال كروت جميع اللاعبين الأربعة سرياً قبل بدء التحقق الرقمي."
                    } else if (totalSelected.size != 52 || hasDuplicates) {
                        validationError = "كشف تداخل بالأوراق! تم رصد كروت مكررة بين اللاعبين أو أوراق غير موزعة بالكامل. تم إعادة تصفير الإدخالات لحماية أمان الطاولة. يرجى التوزيع والإدخال مرة أخرى بعناية."
                        viewModel.playerHands.clear()
                    } else {
                        // إعادة دمج مصفوفة الأوراق وتفويض الملكيات بشكل مؤمن
                        val freshDeck = viewModel.cards.value
                        freshDeck.forEach { card ->
                            val matched = totalSelected.find { it.id == card.id }
                            card.ownerId = matched?.ownerId
                            card.isPlayed = false
                        }
                        viewModel.cards.value = freshDeck
                        viewModel.currentTurnPlayerId = viewModel.startingPlayerId
                        viewModel.isGameStarted = true
                        viewModel.logEvent("بدأت الجولة المؤمنة! الأوراق محصنة وتوزيع الـ 52 ورقة سليم 100%.", LedgerType.SYSTEM)
                        viewModel.logEvent("مملكة تركس الإيجابية جاهزة. الدور يبدأ عند: ${viewModel.playerNames[viewModel.currentTurnPlayerId - 1]}", LedgerType.SUCCESS)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(8.dp, RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC59F2D), // زر ذهبي مذهل
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تدقيق الطاولة وتفعيل درع النزاهة الرقمي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// مكون الاختيار السري المعمى للسرية التامة (Anti-Peeking Portal)
@Composable
fun SecretHandInputComponent(
    playerId: Int,
    playerName: String,
    onHandConfirmed: (List<TrexCard>) -> Unit,
    onCancel: () -> Unit
) {
    var isMasked by remember { mutableStateOf(true) }
    val localDeck = remember {
        // توليد نسخة محلية للاختيار من بينها
        val list = mutableListOf<TrexCard>()
        var id = 1
        for (suit in Suit.values()) {
            for (value in 1..13) {
                list.add(TrexCard(id++, value, suit))
            }
        }
        list
    }
    val selectedCards = remember { mutableStateListOf<TrexCard>() }

    if (isMasked) {
        // بوابة تعمية الشاشة لمنع التلصص
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF04100B))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFD4AF37),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "بوابة الإدخال المؤمنة والسرية",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "اللاعب الحالي لتسليم الأوراق: $playerName",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "يرجى من اللاعبين الآخرين عدم النظر للشاشة. اضغط على الزر أدناه لكشف الشاشة وإدخال أوراقك الـ 13 سرياً.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { isMasked = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("كشف الأوراق والمتابعة سرياً", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(onClick = onCancel) {
                    Text("إلغاء والعودة", color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    } else {
        // واجهة إدخال الكروت
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "اختر كروت $playerName",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "المختار: ${selectedCards.size} من أصل 13 ورقة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedCards.size == 13) Color(0xFF4CAF50) else Color(0xFFD4AF37)
                    )
                }
                
                // زر قفل الشاشة وإخفائها مجدداً
                IconButton(
                    onClick = { isMasked = true },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.VisibilityOff, contentDescription = "قفل", tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // شبكة كروت الاختيار الـ 52 كاملة
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(localDeck) { card ->
                    val isSelected = selectedCards.any { it.id == card.id }
                    CardSelectUI(
                        card = card,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                selectedCards.removeAll { it.id == card.id }
                            } else if (selectedCards.size < 13) {
                                selectedCards.add(card)
                            }
                        }
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { isMasked = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Text("تأمين وتعمية الشاشة")
                }
                
                Button(
                    onClick = { onHandConfirmed(selectedCards.toList().onEach { it.ownerId = playerId }) },
                    enabled = selectedCards.size == 13,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4AF37),
                        disabledContainerColor = Color.White.copy(alpha = 0.1f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Text("تأكيد وقفل الأوراق سرياً", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =========================================================================
// 5. واجهة اللعب المركزية الفخمة وتصميم الطاولة الحقيقية
// =========================================================================

@Composable
fun MainGamePlayScreen(viewModel: TrexViewModel) {
    var showSelectCardDialog by remember { mutableStateOf(false) }
    
    // الفحص البرمجي الصامت لوجود أي أوراق صالحة للعب في يد اللاعب الحالي
    val hasLegalMoves = viewModel.hasAvailableMoves(viewModel.currentTurnPlayerId)
    val currentPlayerName = viewModel.playerNames[viewModel.currentTurnPlayerId - 1]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // شريط الرأس مع مؤشر النزاهة والإحصاءات العامة
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C241B)),
            border = BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (viewModel.fraudAttempts.value > 0) Color(0xFFD32F2F) else Color(0xFF4CAF50),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "درع تركس المؤمن",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مؤشر النزاهة: ${if (viewModel.fraudAttempts.value == 0) "100% ناصع" else "مراقب وتنبيهات"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (viewModel.fraudAttempts.value == 0) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("المنبثق الملعوب", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        Text("${viewModel.totalPlayedCount.value}/52", style = MaterialTheme.typography.titleMedium, color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("محاولات الغش", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        Text("${viewModel.fraudAttempts.value}", style = MaterialTheme.typography.titleMedium, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // لوحة الدور والتحكم الفعالة
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF133629)),
            border = BorderStroke(2.dp, Color(0xFFD4AF37))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الدور الحالي عند: $currentPlayerName",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // بطاقة تنبيه ذكي لللعب
                Text(
                    text = if (hasLegalMoves) "يتوجب على اللاعب إلقاء ورقة صالحة من يده." else "اللاعب لا يملك أي أوراق قانونية، مسموح له بالمرير (Pass).",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasLegalMoves) Color(0xFF81C784) else Color(0xFFE57373)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // زر التمرير (Pass) الآمن
                    Button(
                        onClick = { viewModel.attemptPass() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B1919),
                            disabledContainerColor = Color.White.copy(alpha = 0.05f),
                            disabledContentColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pass", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // زر اختيار كرت ملعوب على الواقع
                    Button(
                        onClick = { showSelectCardDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC59F2D)),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AddCircle, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تسجيل ورقة ملعوبة", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }

        // طاولة تركس الكبرى (The Grand Trex Board Grid sorted by suit rows)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF04100B)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Text(
                    text = "أوراق الطاولة المفتوحة حالياً:",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // نقوم بعرض الطاولة مقسمة لأربعة صفوف منسقة (كل صف يمثل لوناً) لسرعة القراءة والمسح البصري
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Suit.values().forEach { suit ->
                        val bounds = viewModel.getSuitPlayableBounds(suit)
                        val suitPlayedCards = viewModel.cards.value.filter { it.suit == suit && it.isPlayed }.sortedBy { it.value }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF081C15), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // رأس السهم / لون الكرت
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(getSuitColor(suit).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = getSuitChar(suit),
                                    color = getSuitColor(suit),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(10.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                if (suitPlayedCards.isEmpty()) {
                                    Text(
                                        text = "مغلق 🔒 (يتطلب الولد 11 للفتح)",
                                        color = Color.White.copy(alpha = 0.4f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    // عرض الكروت الملعوبة في هذا اللون في خط زمني مرن
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        suitPlayedCards.forEach { card ->
                                            Box(
                                                modifier = Modifier
                                                    .background(Color.White, RoundedCornerShape(4.dp))
                                                    .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = getCardValueStr(card),
                                                    color = getSuitColor(suit),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(2.dp))
                                    
                                    // مؤشر المتاح اللحظي
                                    bounds?.let {
                                        val nextMin = it.first - 1
                                        val nextMax = it.second + 1
                                        val availables = mutableListOf<String>()
                                        if (nextMin >= 1) availables.add(getCardValueName(nextMin))
                                        if (nextMax <= 13) availables.add(getCardValueName(nextMax))
                                        
                                        Text(
                                            text = "المتاح التالي: ${availables.joinToString(" أو ")}",
                                            color = Color(0xFFD4AF37),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // سجل النزاهة والعمليات اللحظية (The Integrity Ledger Logs Console)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF06140F)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null, tint = Color(0xFFD4AF37), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("سجل النزاهة والتحقق اللحظي", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    TextButton(onClick = { viewModel.resetGame() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إعادة تصفير الجولة", color = Color(0xFFD32F2F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.ledger, key = { it.id }) { log ->
                        val (logColor, logIcon) = when (log.type) {
                            LedgerType.SUCCESS -> Pair(Color(0xFF81C784), "✓")
                            LedgerType.WARNING -> Pair(Color(0xFFFFB74D), "⚠")
                            LedgerType.SECURITY_BREACH -> Pair(Color(0xFFE57373), "✖")
                            LedgerType.SYSTEM -> Pair(Color(0xFF64B5F6), "ℹ")
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(text = "[${log.timestamp}] ", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                            Text(text = "$logIcon ", color = logColor, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(text = log.message, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // تنبيهات الخبث / الأمان الذاتية المنبثقة
    if (viewModel.isAlertActive) {
        AlertDialog(
            onDismissRequest = { viewModel.isAlertActive = false },
            icon = {
                Icon(
                    imageVector = if (viewModel.alertIsError) Icons.Default.Gavel else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (viewModel.alertIsError) Color(0xFFD32F2F) else Color(0xFF4CAF50),
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = if (viewModel.alertIsError) "تنبيه غش / حركة خاطئة" else "حركة مأهولة وشرعية",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = viewModel.alertMessage,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.isAlertActive = false },
                    colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.alertIsError) Color(0xFFD32F2F) else Color(0xFF4CAF50))
                ) {
                    Text("فهمت وموافق", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            containerColor = Color(0xFF0E221B),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // نافذة اختيار الكرت الملعوب الفعلي (مستحضر من الواقع للتحقق)
    if (showSelectCardDialog) {
        Dialog(onDismissRequest = { showSelectCardDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1F17)),
                border = BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "اختر الورقة الملقاة بالواقع لـ $currentPlayerName",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ملاحظة: تظهر الـ 52 ورقة بالكامل لكشف الغش ومحاولة لعب أوراق الغير.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(viewModel.cards.value) { card ->
                            val isAlreadyPlayed = card.isPlayed
                            
                            CardSelectUI(
                                card = card,
                                isSelected = false,
                                isDarkened = isAlreadyPlayed,
                                onClick = {
                                    if (!isAlreadyPlayed) {
                                        val success = viewModel.attemptPlayCard(card.id)
                                        if (success) {
                                            showSelectCardDialog = false
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { showSelectCardDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إلغاء النافذة", color = Color.White)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 6. مكونات واجهة رسوم الكروت المساعدة (UI Helper Elements)
// =========================================================================

@Composable
fun CardSelectUI(
    card: TrexCard,
    isSelected: Boolean,
    isDarkened: Boolean = false,
    onClick: () -> Unit
) {
    val suitColor = getSuitColor(card.suit)
    val suitChar = getSuitChar(card.suit)
    val valueStr = getCardValueStr(card)

    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(0.72f)
            .shadow(
                elevation = if (isSelected) 6.dp else 2.dp,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = when {
                    isDarkened -> Color.White.copy(alpha = 0.15f)
                    isSelected -> Color(0xFFF9E79F) // خلفية ذهبية ناعمة للمختار
                    else -> Color.White
                },
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFFD4AF37) else Color.Gray.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = !isDarkened, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Text(
                text = valueStr,
                color = if (isDarkened) Color.White.copy(alpha = 0.3f) else suitColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = suitChar,
                color = if (isDarkened) Color.White.copy(alpha = 0.3f) else suitColor,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// دوال مساعدة لاستخراج خصائص الكروت
fun getSuitColor(suit: Suit): Color = when (suit) {
    Suit.HEART, Suit.DIAMOND -> Color(0xFFD32F2F) // أحمر
    Suit.SPADE, Suit.CLUB -> Color(0xFF212121)   // أسود داكن
}

fun getSuitChar(suit: Suit): String = when (suit) {
    Suit.HEART -> "♥"
    Suit.DIAMOND -> "♦"
    Suit.CLUB -> "♣"
    Suit.SPADE -> "♠"
}

fun getCardValueStr(card: TrexCard): String = when (card.value) {
    1 -> "A"
    11 -> "J"
    12 -> "Q"
    13 -> "K"
    else -> card.value.toString()
}

fun getCardValueName(value: Int): String = when (value) {
    1 -> "A"
    11 -> "J"
    12 -> "Q"
    13 -> "K"
    else -> value.toString()
}
