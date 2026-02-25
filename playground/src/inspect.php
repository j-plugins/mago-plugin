<?php

declare(strict_types=1);

$test = 'nice';
$test2 = $test . '2';

function test(): true
{
    return true;
}

/** @mago-ignore lint:no-debug-symbols */
\Mago\inspect($test, test(), $test2);
