package io.github.kingg22.godot.codegen.impl.extensionapi.knative.impl

import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import io.github.kingg22.godot.codegen.models.extensionapi.domain.ResolvedBuiltinLayout

fun buildLayoutConstants(layout: ResolvedBuiltinLayout): List<PropertySpec> =
    layout.memberOffsets.map { (member, offset) ->
        PropertySpec
            .builder("OFFSET_${member.uppercase()}", INT, KModifier.CONST)
            .initializer("%L", offset)
            .addKdoc(
                "Byte offset of member `%L` for build configuration `%L`.",
                member,
                layout.buildConfiguration.jsonName,
            )
            .build()
    }
