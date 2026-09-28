scoreboard objectives add shock_lunge dummy
scoreboard objectives add shock_lunge_ok dummy
scoreboard objectives add shock_lunge_auth dummy
scoreboard players add #clock shock_lunge 0
execute unless score #duration shock_lunge matches 1.. run scoreboard players set #duration shock_lunge 400
scoreboard players set * shock_lunge_auth 0
