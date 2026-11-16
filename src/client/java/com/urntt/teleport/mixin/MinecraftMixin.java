package com.urntt.teleport.mixin;

import com.urntt.teleport.clicktp.ClickTp;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets Click TP take over the use key. The hook runs right after vanilla sets the 4-tick delay between uses, so
 * holding the key repeats teleports at vanilla's pace. When Click TP does not teleport, vanilla uses the item or block
 * as usual.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(
			method = "startUseItem",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;rightClickDelay:I", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
			cancellable = true)
	private void teleport$clickTp(final CallbackInfo ci) {
		if (ClickTp.onUse((Minecraft) (Object) this)) {
			ci.cancel();
		}
	}
}
