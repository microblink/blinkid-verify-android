package com.microblink.blinkidverify.sample.ui.result

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Gray
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microblink.blinkidverify.core.data.model.checks.DetailedCheck
import com.microblink.blinkidverify.core.data.model.checks.FieldCheck
import com.microblink.blinkidverify.core.data.model.checks.TieredCheck
import com.microblink.blinkidverify.core.data.model.checks.VerifyCheck
import com.microblink.blinkidverify.core.data.model.result.BlinkIdVerifyV3EndpointResponse
import com.microblink.blinkidverify.core.data.model.result.extractionProcessingStatus
import com.microblink.blinkidverify.core.data.model.result.verifyVerdictOrRaw
import com.microblink.blinkidverify.sample.ui.theme.Cobalt
import com.microblink.blinkidverify.sample.ui.theme.Cobalt400
import com.microblink.blinkidverify.sample.ui.theme.Cobalt50
import com.microblink.blinkidverify.sample.ui.theme.Cobalt800
import com.microblink.blinkidverify.sample.ui.theme.DeepBlue
import kotlinx.serialization.json.Json

@Composable
fun VerifySampleResultScreen(
    result: BlinkIdVerifyV3EndpointResponse,
    onNavigateUp: () -> Unit,
) {
    val json = Json {
        prettyPrint = true
        explicitNulls = false
    }

    val resultList = mutableListOf<Result>().apply {
        result.pipeline.extraction?.status?.let { extractionPipelineStatus ->
            add(
                SampleResult(
                    title = "Pipeline — extraction",
                    value = extractionPipelineStatus,
                )
            )
        }
        result.pipeline.verification?.status?.let { verificationPipelineStatus ->
            add(
                SampleResult(
                    title = "Pipeline — verification",
                    value = verificationPipelineStatus,
                )
            )
        }
        result.extractionProcessingStatus()?.let { processingStatus ->
            add(
                SampleResult(
                    title = "Extraction processing status",
                    value = processingStatus,
                )
            )
        }
        add(
            SampleResult(
                title = "Verdict",
                value = result.verification.verifyVerdictOrRaw(),
            )
        )
        if (result.verification.failedChecks.isNotEmpty()) {
            add(
                SampleResult(
                    title = "Failed checks",
                    value = result.verification.failedChecks.joinToString(separator = "\n"),
                )
            )
        }
        result.verification.checks?.let { checks ->
            add(
                SampleResult(
                    title = "Verification checks",
                    value = json.encodeToString(
                        kotlinx.serialization.json.JsonElement.serializer(),
                        checks,
                    ),
                )
            )
        }
        result.messages?.forEach {
            add(
                SampleResult(
                    title = it.code,
                    value = it.message,
                )
            )
        }
        result.images?.let { images ->
            add(
                SampleResult(
                    title = "Images",
                    value = buildString {
                        if (images.face != null) appendLine("face: present")
                        if (images.signature != null) appendLine("signature: present")
                        if (images.firstSideCropped != null) appendLine("firstSideCropped: present")
                        if (images.secondSideCropped != null) appendLine("secondSideCropped: present")
                        if (images.barcode != null) append("barcode: present")
                    }.ifBlank { "None" },
                )
            )
        }
        add(
            SampleResult(
                title = "Runtime",
                value = json.encodeToString(result.runtime),
            )
        )
        add(
            SampleResult(
                title = "Extraction",
                value = json.encodeToString(
                    kotlinx.serialization.json.JsonElement.serializer(),
                    result.extraction,
                ),
            )
        )
        add(
            SampleResult(
                title = "Configuration used",
                value = json.encodeToString(
                    kotlinx.serialization.json.JsonElement.serializer(),
                    result.configurationUsed,
                ),
            )
        )
        add(
            SampleResult(
                title = "Image assessment",
                value = json.encodeToString(result.imageAssessment),
            )
        )
    }

    SampleResultScreen(
        results = resultList

    ) {
        onNavigateUp()
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleResultScreen(
    results: List<Result>?,
    onNavigateUp: () -> Unit,
) {
    BackHandler(true, onNavigateUp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Document Verify Test App", style = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp,
                            lineHeight = 24.sp,
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = Cobalt800,
                    titleContentColor = Color.White
                )
            )
        },
        content = { padding ->
            Surface(
                modifier = Modifier
                    .padding(top = padding.calculateTopPadding())
                    .fillMaxSize(),
                color = Cobalt50
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Cobalt50)
                ) {
                    results?.let {
                        if (results.isNotEmpty()) {
                            items(items = results, itemContent = {
                                SampleResultRow(
                                    node = it,
                                    level = 0,
                                    onClick = {}
                                )
                            })
                        }
                    }
                }
            }
        })
}


@Composable
fun SampleResultRow(
    node: Result,
    level: Int,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    var expanded by rememberSaveable {
        mutableStateOf(true)
    }
    Column(
        modifier = Modifier
            .animateContentSize(
                spring(
                    stiffness = Spring.StiffnessLow,
                    visibilityThreshold = IntSize.VisibilityThreshold,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            )
    ) {
        if (node is ResultVerifyCheck) {
            SampleResultRowVerifyCheck(node.verifyCheck, level, expanded, showDivider) {
                expanded = !expanded
                onClick()
            }
            if (expanded) {
                node.children?.let {
                    it.forEach {
                        SampleResultRow(
                            node = it.toResultVerifyCheck(), level = level + 1,
                            onClick = onClick
                        )
                    }
                }
            }

        } else if (node is SampleResult) {
            SampleResultRowText(node, level, expanded, showDivider)
            if (expanded) {
                node.children?.let {
                    it.forEach {
                        SampleResultRow(
                            node = it, level = level + 1,
                            onClick = onClick
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : VerifyCheck> SampleResultRowVerifyCheck(
    check: T,
    level: Int,
    isExpanded: Boolean,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    check.name?.let {
        val interactionSource = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .background(if (level == 0) Cobalt50 else Color.White)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(start = (10 * level).dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    onClick()
                },
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(0.85f)
            ) {
                Text(
                    modifier = Modifier.padding(bottom = 4.dp),
                    text = check.name.toString().uppercase(),
                    style = ResultTitleText,
                    color = if (level == 0) Cobalt800 else if (level == 1) Cobalt else Cobalt400
                )
                check.result?.let { result ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            modifier = Modifier,
                            text = result.name,
                            style = ResultValueText
                        )
                        check.performedChecks?.let {
                            Text(
                                modifier = Modifier,
                                text = "${check.performedChecks.toString()} checks",
                                style = ResultValueText
                            )
                        }
                    }
                }
                check.details?.let {
                    VerifyCheckRow("", it.toString())
                }
                if (check is DetailedCheck) {
                    check.recommendedOutcome?.let {
                        VerifyCheckRow("Recommended outcome", it)
                    }
                    check.certaintyLevel?.let {
                        VerifyCheckRow("Certainty level", it.name)
                    }
                } else if (check is FieldCheck) {
                    VerifyCheckRow("Field type", check.field.name)
                } else if (check is TieredCheck) {
                    check.passesAtOrBelowSensitivity?.let {
                        VerifyCheckRow("Passes at or below sensitivity", it.name)
                    } ?: check.matchLevel?.let {
                        VerifyCheckRow("Match level", it.name)
                    }
                }
            }

            check.checks?.let {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 90f else 0f,
                    label = ""
                )
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.15f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(start = 8.dp, end = 16.dp)
                            .rotate(chevronRotation),
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                }
            }
        }


        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth(),

                color = DeepBlue,
                thickness = if (level == 0) 2.dp else 1.dp
            )
        }
    }
}

@Composable
fun VerifyCheckRow(title: String, value: String) {
    HorizontalDivider(color = Gray)
    Row(
        Modifier
            .padding(10.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title)
        Text(
            text = value,
            style = ResultValueText
        )
    }
}

@Composable
fun SampleResultRowText(
    result: SampleResult,
    level: Int,
    isExpanded: Boolean,
    showDivider: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .background(if (level == 0) Cobalt50 else Color.White)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .padding(start = (10 * level).dp)
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { },
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(0.85f)
        ) {
            Text(
                modifier = Modifier.padding(bottom = 4.dp),
                text = result.title.toString().uppercase(),
                style = ResultTitleText,
                color = if (level == 0) Cobalt800 else if (level == 1) Cobalt else Cobalt400
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    modifier = Modifier,
                    text = result.value,
                    style = ResultValueText
                )
                result.subValue?.let {
                    Text(
                        modifier = Modifier,
                        text = it,
                        style = ResultValueText
                    )
                }
            }
        }
        result.children?.let {
            val chevronRotation by animateFloatAsState(
                targetValue = if (isExpanded) 90f else 0f,
                label = ""
            )
            Column(
                modifier = Modifier
                    .weight(0.15f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                Icon(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 16.dp)
                        .rotate(chevronRotation),
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null
                )
            }
        }
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth(),
            color = DeepBlue,
            thickness = if (level == 0) 2.dp else 1.dp
        )
    }
}