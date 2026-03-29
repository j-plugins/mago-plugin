<?php

/**
 * My Class
 *
 * @package BIG
 */
class Test
{
    /**
     * My function
     *
     * @return void
     */
    public function test()
    {
        nice(); // Ignore without a comment

        /** Test */
        nice(); // Ignore on an existing single-line comment

        /**
         * Test
         */
        nice(); // Ignore on an existing multi-line comment

        /**
         * Test
         *
         * @mago-ignore analysis:test
         *
         * Test
         */
        nice(); // Append to an existing comment in the middle
    }
}

function test()
{
    nice();
}
