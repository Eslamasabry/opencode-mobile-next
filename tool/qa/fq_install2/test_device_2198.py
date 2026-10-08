import unittest
from xml.etree.ElementTree import Element
from device_2198 import account_mask_boxes

class Tests(unittest.TestCase):
    def test_masks_claude_and_signed_in_account_nodes_without_returning_copy(self):
        nodes=[Element('node',{'content-desc':'Claude Code\nSigned in as synthetic-account','bounds':'[10,20][100,80]'}),
               Element('node',{'text':'Signed in as second-account','bounds':'[30,90][300,140]'}),
               Element('node',{'text':'fx\nSign in needed','bounds':'[0,200][400,260]'})]
        self.assertEqual(account_mask_boxes(nodes),[(10,20,100,80),(30,90,300,140)])
        self.assertNotIn('account',repr(account_mask_boxes(nodes)))
    def test_account_without_safe_bounds_refuses_screenshot(self):
        with self.assertRaises(RuntimeError):
            account_mask_boxes([Element('node',{'text':'Signed in as synthetic-account','bounds':''})])

if __name__=='__main__':unittest.main()
