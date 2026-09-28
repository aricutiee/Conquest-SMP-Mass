scoreboard players add #clock shock_lunge 1
scoreboard players add @a shock_lunge 0
execute as @a if score @s shock_lunge <= #clock shock_lunge run scoreboard players set @s shock_lunge_ok 1
