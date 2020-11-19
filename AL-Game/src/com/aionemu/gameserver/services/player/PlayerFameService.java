/**
 * This file is part of Aion-Lightning <aion-lightning.org>.
 *
 *  Aion-Lightning is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Aion-Lightning is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details. *
 *  You should have received a copy of the GNU General Public License
 *  along with Aion-Lightning.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver.services.player;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.gameserver.dao.PlayerFameDAO;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.fame.FameExp;
import com.aionemu.gameserver.model.gameobjects.player.fame.PlayerFame;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PLAYER_FAME;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.utils.PacketSendUtility;

public class PlayerFameService {

    private static final Logger log = LoggerFactory.getLogger(PlayerFameService.class);
    public int MAX_LEVEL = 9;

    public void init() {
        log.info("[PlayerFameService] loaded ...");
    }

    public void onResetWeekly() {
        List<PlayerFame> famesReduce = this.getDao().weeklyFame();
        for (PlayerFame fame : famesReduce) {
            long reduce = Math.round(fame.getExp() * 0);
            if (fame.getExp() - reduce <= 0) {
                fame.setLevel(fame.getLevel() - 1);
                long changeExp = this.getExpForLevel(fame.getLevel()) - Math.round((double)fame.getExp() * 0.015);
                fame.setExp(changeExp);
                this.getDao().reduceWeekly(fame);
                continue;
            }
            fame.setExp(fame.getExp() - reduce);
            this.getDao().reduceWeekly(fame);
        }
        log.info("[PlayerFameService] : Weekly Reduce Finish");
    }

    public void recoverExpFame(Player player) {
        PlayerFame fame = this.fameLevelByWorld(player, player.getWorldId());
        if (fame != null) {
            this.addFameExp(player, fame.getExpLoss());
            fame.setExpLoss(0);
            this.getDao().updatePlayerFame(player, fame);
            PacketSendUtility.sendPacket(player, new SM_PLAYER_FAME(player));
        }
    }

    public void onPlayerLogin(Player player) {
        player.setPlayerFame(this.getDao().loadPlayerFame(player));
        for (int i = 1; i < 7; ++i) {
            if (player.getPlayerFame().containsKey(i)) continue;
            PlayerFame fame = new PlayerFame(i, 1, 0, 0, player.getObjectId());
            player.getPlayerFame().put(fame.getId(), fame);
            this.getDao().addPlayerFame(player, fame);
        }
        PacketSendUtility.sendPacket(player, new SM_PLAYER_FAME(player));
    }

    public void addFameExp(Player player, long points) {
        for (PlayerFame playerFame : player.getPlayerFame().values()) {
            if (player.getWorldId() != playerFame.getFameEnum().getWorldId()) continue;
            long exp = playerFame.getExp();
            if (playerFame.getLevel() == this.MAX_LEVEL && exp + points > this.getExpForLevel(playerFame.getLevel())) {
                playerFame.setExp(this.getExpForLevel(9));
                PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_GET_FEXP(playerFame.getLevel())); // Test
                this.getDao().updatePlayerFame(player, playerFame);
                continue;
            }
            if (exp + points >= this.getExpForLevel(playerFame.getLevel())) {
                long diff = exp + points - this.getExpForLevel(playerFame.getLevel());
                playerFame.setLevel(playerFame.getLevel() + 1);
                PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_FAME_CHANGE_LEVEL_DONE(playerFame.getFameEnum().getDescriptionId(), playerFame.getLevel())); // Test
                playerFame.setExp(diff);
                this.getDao().updatePlayerFame(player, playerFame);
                continue;
            }
            playerFame.setExp(exp + points);
            this.getDao().updatePlayerFame(player, playerFame);
        }
        PacketSendUtility.sendPacket(player, new SM_PLAYER_FAME(player));
    }

    public void onPlayerDie(Player player) {
        for (PlayerFame playerFame : player.getPlayerFame().values()) {
            if (player.getWorldId() != playerFame.getFameEnum().getWorldId() || playerFame.getExp() == 0) continue;
            int loss = Math.round(playerFame.getExp() * 0);
            int unrecoverable = (int)((double)loss * 0.22222222);
            int recoverable = loss - unrecoverable;
            if (playerFame.getLevel() - loss < 0) {
                playerFame.setExp(0);
            } else {
                playerFame.setExp(playerFame.getExp() - (long)loss);
            }
            playerFame.setExpLoss(playerFame.getExpLoss() + (long)recoverable);
            this.getDao().updatePlayerFame(player, playerFame);
        }
        PacketSendUtility.sendPacket(player, new SM_PLAYER_FAME(player));
    }

    public PlayerFame fameLevelByWorld(Player player, int worldId) {
        PlayerFame playerFame = null;
        for (PlayerFame fame : player.getPlayerFame().values()) {
            if (fame.getFameEnum().getWorldId() != worldId) continue;
            playerFame = fame;
        }
        return playerFame;
    }

    public long getExpForLevel(int level) {
        return FameExp.getFameExp(level).getExp();
    }

    public PlayerFameDAO getDao() {
        return DAOManager.getDAO(PlayerFameDAO.class);
    }

	public static PlayerFameService getInstance() {
		return NewSingletonHolder.INSTANCE;
	}

	private static class NewSingletonHolder {

		private static final PlayerFameService INSTANCE = new PlayerFameService();
	}
}

