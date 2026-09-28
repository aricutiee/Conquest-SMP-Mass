scoreboard objectives add conq_lunges dummy
scoreboard objectives add conq_lunge_ok dummy
scoreboard objectives add conq_lunge_sys dummy
scoreboard players set #version conq_lunge_sys 1
execute unless score #limit conq_lunge_sys matches 1.. run scoreboard players set #limit conq_lunge_sys 3
scoreboard players set * conq_lunge_ok 0
