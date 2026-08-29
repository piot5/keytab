package com.piotv.keytab

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.piotv.keytab.file.FileManagerFragment
import com.piotv.keytab.ime.KeyboardInfoFragment

class MainActivity : AppCompatActivity() {

    private class SectionsPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> FileManagerFragment()
            else -> KeyboardInfoFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pager = findViewById<ViewPager2>(R.id.pager)
        val tabs = findViewById<TabLayout>(R.id.main_tabs)

        pager.adapter = SectionsPagerAdapter(this)
        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = getString(
                if (position == 0) R.string.tab_files else R.string.tab_keyboard
            )
        }.attach()
    }
}
