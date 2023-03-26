package org.teacon.slides;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.ParametersAreNonnullByDefault;

@Mod(SlideShow.ID)
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideShow {
    public static final String ID = "slide_show"; // as well as the namespace

    public static final Logger LOGGER = LogManager.getLogger("SlideShow");
}
