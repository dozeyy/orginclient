package com.origin.client.client.mixin;

import com.origin.client.client.mods.Mods;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Chat mod, message-side behaviour:
//   Timestamps — prepends a muted [HH:mm]. 1.18.2 predates the chat-signature
//                era entirely: the public addMessage(Component) delegates to
//                the private addMessage(Component,int), which every NEW message
//                funnels through (bytecode-verified). rescaleChat() re-adds via
//                the 4-arg overload with refresh=true, so hooking the 2-arg one
//                never double-stamps on a chat rescale.
//   Stack Spam — when a message repeats the previous one, the earlier copy is
//                removed and the new one gets a running "(xN)" counter, so spam
//                collapses to a single updating line.
// Opacity/scale ride the vanilla accessibility options from the feature tick.
@Mixin(ChatComponent.class)
public abstract class ChatTimestampMixin {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	@Shadow @Final private List<GuiMessage<Component>> allMessages;

	// 1.18.2's re-trim after a mutation is the public rescaleChat()
	// (refreshTrimmedMessage is the later 1.19.x name).
	@Shadow
	public abstract void rescaleChat();

	private static String originclient$lastBase = null;
	private static int originclient$lastCount = 1;

	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;I)V",
			at = @At("HEAD"), argsOnly = true)
	private Component originclient$transform(Component message) {
		if (!Mods.on("chat")) {
			return message;
		}
		Component result = message;

		if (Mods.bool("chat", "stackSpam")) {
			String base = message.getString();
			if (base.equals(originclient$lastBase) && !allMessages.isEmpty()) {
				originclient$lastCount++;
				allMessages.remove(0);          // drop the previous identical line
				rescaleChat();
				result = new TextComponent(base + " ")
						.append(new TextComponent("(x" + originclient$lastCount + ")").withStyle(ChatFormatting.GRAY));
			} else {
				originclient$lastBase = base;
				originclient$lastCount = 1;
			}
		}

		if (Mods.bool("chat", "timestamps")) {
			// Append the message as a sibling of an unstyled root so it keeps its
			// own colour (white) instead of inheriting the timestamp's grey — the
			// old code styled the parent grey and the message inherited it.
			Component stamp = new TextComponent("[" + LocalTime.now().format(TIME) + "] ")
					.withStyle(ChatFormatting.DARK_GRAY);
			result = new TextComponent("").append(stamp).append(result);
		}
		return result;
	}
}
