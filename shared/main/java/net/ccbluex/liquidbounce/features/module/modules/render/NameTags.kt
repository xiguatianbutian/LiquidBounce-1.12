/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.api.minecraft.client.entity.IEntityLivingBase
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.Render3DEvent
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.features.module.modules.misc.AntiBot
import net.ccbluex.liquidbounce.injection.backend.Backend
import net.ccbluex.liquidbounce.injection.backend.GlStateManagerImpl.resetColor
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.EntityUtils
import net.ccbluex.liquidbounce.utils.extensions.getPing
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.quickDrawBorderedRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.quickDrawRect
import net.ccbluex.liquidbounce.value.*
import net.minecraft.client.renderer.GlStateManager.*
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.roundToInt

@ModuleInfo(name = "NameTags", description = "Changes the scale of the nametags so you can always read them.", category = ModuleCategory.RENDER)
class NameTags : Module() {
    private val modeValue = ListValue("Mode", arrayOf("Simple","Liquid", "Jello"), "Liquid")
    private val healthValue = BoolValue("Health", true)
    private val pingValue = BoolValue("Ping", true)
    private val distanceValue = BoolValue("Distance", false)
    private val armorValue = BoolValue("Armor", true)
    private val clearNamesValue = BoolValue("ClearNames", false)
    private val fontValue = FontValue("Font", Fonts.font40)
    private val borderValue = BoolValue("Border", true)
    private val scaleValue = FloatValue("Scale", 1F, 1F, 4F)
    private val botValue = BoolValue("Bots", true)
    private val jelloColorValue = BoolValue("JelloHPColor", true).displayable { modeValue.equals("Jello") }
    private val jelloAlphaValue = IntegerValue("JelloAlpha", 170, 0, 255).displayable { modeValue.equals("Jello") }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        glPushAttrib(GL_ENABLE_BIT)
        glPushMatrix()

        // Disable lightning and depth test
        glDisable(GL_LIGHTING)
        glDisable(GL_DEPTH_TEST)

        glEnable(GL_LINE_SMOOTH)

        // Enable blend
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        for (entity in mc.theWorld!!.loadedEntityList) {
            if (!EntityUtils.isSelected(entity, false)) continue
            if (AntiBot.isBot(entity.asEntityLivingBase()) && !botValue.get()) continue

            renderNameTag(entity.asEntityLivingBase(),
                    if (clearNamesValue.get())
                        ColorUtils.stripColor(entity.displayName?.unformattedText) ?: continue
                    else
                        (entity.displayName ?: continue).unformattedText
            )
        }

        glPopMatrix()
        glPopAttrib()

        // Reset color
        glColor4f(1F, 1F, 1F, 1F)
    }

    private fun renderNameTag(entity: IEntityLivingBase, tag: String) {
        val thePlayer = mc.thePlayer ?: return

        val fontRenderer = fontValue.get()

        // Modify tag
        val bot = AntiBot.isBot(entity)
        val nameColor = if (bot) "§3" else if (entity.invisible) "§6" else if (entity.sneaking) "§4" else "§7"
        val ping = if (classProvider.isEntityPlayer(entity)) entity.asEntityPlayer().getPing() else 0
        val distanceText = if (distanceValue.get()) "§7${thePlayer.getDistanceToEntity(entity).roundToInt()}m " else ""
        val pingText = if (pingValue.get() && classProvider.isEntityPlayer(entity)) (if (ping > 200) "§c" else if (ping > 100) "§e" else "§a") + ping + "ms §7" else ""
        val healthText = if (healthValue.get()) "§7§c " + entity.health.toInt() + " HP" else ""
        val botText = if (bot) " §c§lBot" else ""
        val text = "$distanceText$pingText$nameColor$tag$healthText$botText"
        var targetTicks = 0
        // Push
        glPushMatrix()

        // Translate to player position
        val timer = mc.timer
        val renderManager = mc.renderManager


        glTranslated( // Translate to player position with render pos and interpolate it
                entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * timer.renderPartialTicks - renderManager.renderPosX,
                entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * timer.renderPartialTicks - renderManager.renderPosY + entity.eyeHeight.toDouble() + 0.55,
                entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * timer.renderPartialTicks - renderManager.renderPosZ
        )

        glRotatef(-mc.renderManager.playerViewY, 0F, 1F, 0F)
        glRotatef(mc.renderManager.playerViewX, 1F, 0F, 0F)


        // Scale
        var distance = thePlayer.getDistanceToEntity(entity) * 0.25f

        if (distance < 1F)
            distance = 1F

        val scale = distance / 100f * scaleValue.get()

        glScalef(-scale, -scale, scale)

        AWTFontRenderer.assumeNonVolatile = true
        RenderUtils.disableGlCap(GL_LIGHTING, GL_DEPTH_TEST)
        RenderUtils.enableGlCap(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        // Draw NameTag
        when (modeValue.get().toLowerCase()) {
            "simple" -> {
                val healthPercent = (entity.health / entity.maxHealth).coerceAtMost(1F)
                val width = fontRenderer.getStringWidth(tag).coerceAtLeast(30) / 2
                val maxWidth = width * 2 + 12F

                glScalef(-scale * 2, -scale * 2, scale * 2)
                RenderUtils.drawRect(
                    -width + 6F,
                    -fontRenderer.fontHeight * 1.7F,
                    width - 6F,
                    -2F,
                    Color(0, 0, 0, jelloAlphaValue.get())
                )
                RenderUtils.drawRect(width + 6F, 2F, width  + 6F + (maxWidth * healthPercent), 0F, ColorUtils.healthColor(entity.health, entity.maxHealth, jelloAlphaValue.get()))
                RenderUtils.drawRect(
                    width + 6F - (maxWidth * healthPercent),
                    2F,
                    width - 6F,
                    0F,
                    Color(0, 0, 0, jelloAlphaValue.get())
                )
                fontRenderer.drawString(tag, (-fontRenderer.getStringWidth(tag) * 0.5F).toInt(), (-fontRenderer.fontHeight * 1.4F).toInt(), Color.WHITE.rgb)
            }
            "liquid" -> {
                val width = fontRenderer.getStringWidth(text) * 0.5f

                glDisable(GL_TEXTURE_2D)
                glEnable(GL_BLEND)

                if (borderValue.get())
                    quickDrawBorderedRect(-width - 2F, -2F, width + 4F, fontRenderer.fontHeight + 2F, 2F, Color(255, 255, 255, 90).rgb, Integer.MIN_VALUE)
                else
                    quickDrawRect(-width - 2F, -2F, width + 4F, fontRenderer.fontHeight + 2F, Integer.MIN_VALUE)

                glEnable(GL_TEXTURE_2D)

                fontRenderer.drawString(text, 1F + -width, if (fontRenderer == Fonts.minecraftFont) 1F else 1.5F,
                    0xFFFFFF, true)

                AWTFontRenderer.assumeNonVolatile = false

                if (armorValue.get() && classProvider.isEntityPlayer(entity)) {
                    mc.renderItem.zLevel = -147F

                    val indices: IntArray = if (Backend.MINECRAFT_VERSION_MINOR == 8) (0..4).toList().toIntArray() else intArrayOf(0, 1, 2, 3, 5, 4)

                    for (index in indices) {
                        val equipmentInSlot = entity.getEquipmentInSlot(index) ?: continue

                        mc.renderItem.renderItemAndEffectIntoGUI(equipmentInSlot, -50 + index * 20, -22)
                    }

                    enableAlpha()
                    disableBlend()
                    enableTexture2D()
                }
            }
            "jello" -> {
                // colors
                var hpBarColor = Color(255, 255, 255, jelloAlphaValue.get())
                val name = entity.displayName!!.unformattedText
                if (jelloColorValue.get() && name.startsWith("§")) {
                    hpBarColor = ColorUtils.colorCode(name.substring(1, 2), jelloAlphaValue.get())
                }
                val bgColor = Color(50, 50, 50, jelloAlphaValue.get())
                val width = fontRenderer.getStringWidth(tag) / 2
                val maxWidth = (width + 4F) - (-width - 4F)
                var healthPercent = entity.health / entity.maxHealth

                // render bg
                glScalef(-scale * 2, -scale * 2, scale * 2)
                RenderUtils.drawRect(-width - 4F, -fontRenderer.fontHeight * 3F, width + 4F, -3F, bgColor)

                // render hp bar
                if (healthPercent> 1) {
                    healthPercent = 1F
                }

                RenderUtils.drawRect(-width - 4F, -3F, (-width - 4F) + (maxWidth * healthPercent), 1F, hpBarColor)
                RenderUtils.drawRect((-width - 4F) + (maxWidth * healthPercent), -3F, width + 4F, 1F, bgColor)

                // string
                fontRenderer.drawString(tag, -width, -fontRenderer.fontHeight * 2 - 4, Color.WHITE.rgb)
                glScalef(0.5F, 0.5F, 0.5F)
                fontRenderer.drawString("Health: " + entity.health.toInt(), -width * 2, -fontRenderer.fontHeight * 2, Color.WHITE.rgb)
            }
        }
        RenderUtils.resetCaps()
        glColor4f(1F, 1F, 1F, 1F)
        resetColor()
        // Pop
        glPopMatrix()
    }
}
