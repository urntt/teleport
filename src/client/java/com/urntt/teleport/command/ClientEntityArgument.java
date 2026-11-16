package com.urntt.teleport.command;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;

/**
 * Vanilla's single-entity argument, except that target selectors are always allowed. The client evaluates them
 * itself, so the player's permission to use selectors on the server does not matter.
 */
public final class ClientEntityArgument extends EntityArgument {
	private ClientEntityArgument() {
		super(true, false);
	}

	public static ClientEntityArgument entity() {
		return new ClientEntityArgument();
	}

	public static EntitySelector getSelector(final CommandContext<?> context, final String name) {
		return context.getArgument(name, EntitySelector.class);
	}

	@Override
	public <S> EntitySelector parse(final StringReader reader, final S source) throws CommandSyntaxException {
		return this.parse(reader);
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
		if (!(context.getSource() instanceof SharedSuggestionProvider source)) {
			return Suggestions.empty();
		}
		StringReader reader = new StringReader(builder.getInput());
		reader.setCursor(builder.getStart());
		EntitySelectorParser parser = new EntitySelectorParser(reader, true);
		try {
			parser.parse();
		} catch (CommandSyntaxException ignored) {
			// Suggestions are still offered for incomplete input.
		}
		return parser.fillSuggestions(builder, suggestions -> {
			Collection<String> names = source.getOnlinePlayerNames();
			SharedSuggestionProvider.suggest(Iterables.concat(names, source.getSelectedEntities()), suggestions);
		});
	}
}
