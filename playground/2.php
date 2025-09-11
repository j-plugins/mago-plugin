<?php

declare(strict_types=1);

function my_func(int $int)
{
    $a = 1 + '';
    return str_repeat('a', $int);
}