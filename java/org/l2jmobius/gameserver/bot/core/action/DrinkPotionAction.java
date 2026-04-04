/*
 * Bot Orchestrator — internal bot management system.
 */
package org.l2jmobius.gameserver.bot.core.action;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.bot.core.exception.FatalBotException;
import org.l2jmobius.gameserver.bot.core.exception.RecoverableBotException;
import org.l2jmobius.gameserver.bot.core.exception.ValidationBotException;
import org.l2jmobius.gameserver.bot.core.model.BotInstance;
import org.l2jmobius.gameserver.bot.core.service.PotionData;
import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.EtcItem;
import org.l2jmobius.gameserver.model.item.instance.Item;

/**
 * Uses the best available healing potion from the bot's inventory.
 * <p>
 * One-shot action: tries once per execution, then is done — the item's own
 * reuse timer prevents over-consumption across repeated calls.
 */
public class DrinkPotionAction implements BotAction
{
	private static final Logger LOGGER = Logger.getLogger(DrinkPotionAction.class.getName());


	private boolean _done = false;

	@Override
	public void execute(BotInstance bot, long now) throws FatalBotException, ValidationBotException, RecoverableBotException
	{
		final Player player = bot.getPlayer();
		for (int itemId : PotionData.HEAL_POTION_IDS)
		{
			final Item potion = player.getInventory().getItemByItemId(itemId);
			if (potion == null)
			{
				continue;
			}

			final EtcItem etcItem = potion.getEtcItem();
			if (etcItem == null)
			{
				continue;
			}

			final IItemHandler handler = ItemHandler.getInstance().getHandler(etcItem);
			if (handler == null)
			{
				continue;
			}

			if (handler.onItemUse(player, potion, false))
			{
				LOGGER.fine("DrinkPotionAction: " + player.getName() + " used " + potion.getName());
			}
			break; // one attempt per action, regardless of success
		}
		// Set reuse guard regardless of outcome — prevents Brain from
		// requesting another potion every tick while the item is on cooldown.
		bot.setNextPotionTime(now + PotionData.POTION_REUSE_MS);
		_done = true;
	}

	@Override
	public boolean isDone(BotInstance bot, long now)
	{
		return _done;
	}
}
