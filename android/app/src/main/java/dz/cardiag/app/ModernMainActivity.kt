
@Composable
private fun HistoryScreen(padding: PaddingValues, ar: Boolean, authenticated: Boolean, openAuth: () -> Unit) {
    var history by remember { mutableStateOf<List<DiagnosticHistory>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() { if (!authenticated) return; scope.launch { loading = true; error = null; runCatching { history = SupabaseClient.client.from("diagnostic_sessions").select(Columns.list("id","complaint","status","language","created_at")).decodeList<DiagnosticHistory>() }.onFailure { error = it.message }; loading = false } }
    LaunchedEffect(authenticated) { if (authenticated) refresh() }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(if (ar) "سجل التشخيص" else "Historique") }
        if (!authenticated) item { AccountRequiredCard(ar, if (ar) "سجّل الدخول للوصول إلى سجل التشخيص." else "Connectez-vous pour accéder à votre historique de diagnostic.", openAuth) }
        if (authenticated) {
            if (loading) items(3) { SkeletonCard() }
            items(history) { h -> Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (h.status == "completed") Icons.Default.CheckCircle else Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.padding(start = 12.dp)) { Text(h.complaint ?: if (ar) "فحص بدون وصف" else "Diagnostic sans symptôme", fontWeight = FontWeight.Bold); Text("${h.status} • ${h.createdAt}", color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
            if (!loading && history.isEmpty()) item { EmptyState(if (ar) "السجل فارغ" else "Historique vide", if (ar) "نتائج التشخيص ستظهر هنا." else "Vos diagnostics apparaîtront ici.") { refresh() } }
        }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues, ar: Boolean, dark: Boolean, authenticated: Boolean, setDark: (Boolean) -> Unit, setLanguage: (String) -> Unit, openAuth: () -> Unit, signOut: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(if (ar) "الإعدادات" else "Réglages") }
        item { AccountRequiredCard(ar, if (authenticated) (if (ar) "الحساب متصل. يمكنك حفظ سياراتك وسجل التشخيص." else "Compte connecté. Vos véhicules et votre historique sont disponibles.") else (if (ar) "استخدام التطبيق كضيف متاح. الحساب اختياري." else "L'utilisation en invité est disponible. Le compte reste facultatif."), if (authenticated) signOut else openAuth, if (authenticated) (if (ar) "تسجيل الخروج" else "Déconnexion") else (if (ar) "تسجيل الدخول (اختياري)" else "Se connecter (facultatif)")) }
        item { SettingRow(Icons.Default.DarkMode, if (ar) "الوضع الداكن" else "Mode sombre", if (ar) "واجهة مريحة للعين" else "Interface confortable", { Switch(checked = dark, onCheckedChange = setDark) }) }
        item { SettingRow(Icons.Default.Language, if (ar) "اللغة" else "Langue", if (ar) "العربية" else "Français", { TextButton(onClick = { setLanguage(if (ar) "fr" else "ar") }) { Text(if (ar) "Français" else "العربية") } }) }
        item { SettingRow(Icons.Default.Security, if (ar) "الخصوصية" else "Confidentialité", if (ar) "بيانات الحساب لا تُستخدم إلا عند تسجيل الدخول." else "Les données du compte sont utilisées uniquement après connexion.", null) }
    }
}

@Composable
private fun AccountRequiredCard(ar: Boolean, body: String, action: () -> Unit, actionLabel: String = if (ar) "تسجيل الدخول" else "Connexion") { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text(if (ar) "الحساب اختياري" else "Compte facultatif", fontWeight = FontWeight.Black) }; Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = action) { Text(actionLabel) } } } }
@Composable private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: @Composable (() -> Unit)?) { Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; action?.invoke() } } }
@Composable private fun EmptyState(title: String, body: String, retry: () -> Unit) { Card { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary); Text(title, fontWeight = FontWeight.Bold); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedButton(onClick = retry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(if (title.contains("vide", true) || title.contains("فارغ")) "Réessayer" else "Retry") } } } }