package com.example.tuoshouji

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class Card(val rank: Int, val suit: Char) {
    val label: String get() = "${when (rank) { 11 -> "J"; 12 -> "Q"; 13 -> "K"; 14 -> "A"; else -> rank }}$suit"
    fun encode() = "$rank|$suit"
    companion object { fun decode(value: String): Card = value.split("|").let { Card(it[0].toInt(), it[1].single()) } }
}

data class PlayerState(val name: String, val human: Boolean, val hand: MutableList<Card> = mutableListOf(), var eliminated: Boolean = false)

class GameEngine(private var playerCount: Int = 4) {
    val players = mutableListOf<PlayerState>()
    val centerPile = mutableListOf<Pair<Card, Int>>()
    var currentPlayer = 0; private set
    var gameOver = false; private set
    var message = "点击新游戏开始"; private set

    init { reset(playerCount) }

    fun reset(count: Int) {
        playerCount = count
        players.clear()
        repeat(count) { players += PlayerState("玩家 ${it + 1}", it == 0) }
        val deck = mutableListOf<Card>()
        for (rank in 2..14) if (count != 3 || rank != 13) for (suit in charArrayOf('♠', '♥', '♣', '♦')) deck += Card(rank, suit)
        deck.shuffle()
        deck.forEachIndexed { index, card -> players[index % count].hand += card }
        centerPile.clear(); currentPlayer = 0; gameOver = false
        message = "${count} 人游戏开始，轮到玩家 1"
    }

    fun playHuman() = play(isHuman = true)
    fun playComputer() = play(isHuman = false)

    private fun play(isHuman: Boolean): Boolean {
        if (gameOver || players[currentPlayer].human != isHuman) return false
        val player = players[currentPlayer]
        val card = player.hand.removeFirstOrNull() ?: run { eliminateEmpty(); advance(); return true }
        centerPile += card to currentPlayer
        val matching = centerPile.indices.filter { centerPile[it].first.rank == card.rank }
        if (matching.size >= 2) {
            val first = matching.first()
            val earned = centerPile.subList(first, centerPile.size).map { it.first }
            player.hand += earned
            repeat(centerPile.size - first) { centerPile.removeAt(first) }
            message = "${player.name} 用 ${card.label} 配对成功，收回 ${earned.size} 张牌"
        } else message = "${player.name} 打出 ${card.label}"
        eliminateEmpty()
        if (!finishIfNeeded()) advance()
        return true
    }

    private fun eliminateEmpty() {
        players.filter { it.hand.isEmpty() && !it.eliminated }.forEach { it.eliminated = true }
    }

    private fun finishIfNeeded(): Boolean {
        val alive = players.filter { !it.eliminated }
        if (alive.size != 1) return false
        gameOver = true
        message = if (alive.single().human) "恭喜，你赢了！" else "游戏结束，${alive.single().name} 获胜"
        return true
    }

    private fun advance() {
        do currentPlayer = (currentPlayer + 1) % playerCount while (players[currentPlayer].eliminated)
        message += "；轮到${players[currentPlayer].name}"
    }

    private fun esc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun unesc(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    fun toJson(): String {
        val playersEncoded = players.joinToString(",") { p ->
            val hand = p.hand.joinToString(".") { esc(it.encode()) }
            "${esc(p.name)}~${p.human}~${p.eliminated}~$hand"
        }
        val centerEncoded = centerPile.joinToString(",") { (card, owner) -> "${esc(card.encode())}~$owner" }
        return listOf(
            "count=$playerCount",
            "turn=$currentPlayer",
            "over=$gameOver",
            "message=${esc(message)}",
            "players=$playersEncoded",
            "center=$centerEncoded"
        ).joinToString(";")
    }

    fun restore(value: String) {
        val state = value.split(";").mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) null else it.substring(0, idx) to it.substring(idx + 1)
        }.toMap()

        playerCount = state["count"]?.toIntOrNull() ?: playerCount
        currentPlayer = state["turn"]?.toIntOrNull() ?: 0
        gameOver = state["over"]?.toBoolean() ?: false
        message = state["message"]?.let(::unesc) ?: ""

        players.clear()
        val playersRaw = state["players"].orEmpty()
        if (playersRaw.isNotEmpty()) {
            playersRaw.split(",").forEach { row ->
                val parts = row.split("~")
                if (parts.size >= 4) {
                    val hand = if (parts[3].isEmpty()) mutableListOf() else parts[3].split(".").map { Card.decode(unesc(it)) }.toMutableList()
                    players += PlayerState(unesc(parts[0]), parts[1].toBoolean(), hand, parts[2].toBoolean())
                }
            }
        }

        centerPile.clear()
        val centerRaw = state["center"].orEmpty()
        if (centerRaw.isNotEmpty()) {
            centerRaw.split(",").forEach { row ->
                val parts = row.split("~")
                if (parts.size >= 2) centerPile += Card.decode(unesc(parts[0])) to (parts[1].toIntOrNull() ?: 0)
            }
        }
    }
}
