#!/usr/bin/python
# -*- coding: UTF-8 -*-

"""
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#
#   Description: PKI TPS CONFIG CLI tests
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#   The following pki tps cli commands needs to be tested:
#   pki tps-config-show
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

#   Author: Sumedh Sidhaye <ssidhaye@redhat.com>
#
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#
#   Copyright (c) 2018 Red Hat, Inc. All rights reserved.
#
#   This copyrighted material is made available to anyone wishing
#   to use, modify, copy, or redistribute it subject to the terms
#   and conditions of the GNU General Public License version 2.
#
#   This program is distributed in the hope that it will be
#   useful, but WITHOUT ANY WARRANTY; without even the implied
#   warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
#   PURPOSE. See the GNU General Public License for more details.
#
#   You should have received a copy of the GNU General Public
#   License along with this program; if not, write to the Free
#   Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
#   Boston, MA 02110-1301, USA.
#
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
"""
import os
import sys

import pytest

try:
    from pki.testlib.common import constants
except Exception:
    if os.path.isfile('/tmp/test_dir/constants.py'):
        sys.path.append('/tmp/test_dir')
        import constants

TPS_CONFIG_VALUES = ['channel.blocksize: 224', 'channel.defKeyIndex: 0', 'channel.defKeyVersion: 0',
                     'channel.encryption: true', 'failover.pod.enable: false', 'general.applet_ext: ijc',
                     'general.search.sizelimit.default: 1',
                     'general.search.sizelimit.max: 1', 'general.search.timelimit.default: 10',
                     'general.search.timelimit.max: 10', 'general.verifyProof: 1']
REVOKED_CERT_MESSAGE = ["FATAL: SSL alert received: CERTIFICATE_REVOKED"]
EXPIRED_CERT_MESSAGE = ["FATAL: SSL alert received: CERTIFICATE_EXPIRED"]

def test_tps_config_help(ansible_module):
    """
    :Title: tps-config help

    :Description: tps-config help

    :Requirement:

    :Setup: Use pki setup via ansible playbooks

    :Steps: 1. Run 'pki tps-config --help'

    :Expectedresults: 1. The command should show the help message for tps-config

    :Automated: Yes
    """
    config_help_output = ansible_module.command('pki tps-config --help')
    for result in config_help_output.values():
        assert "tps-config-mod          Modify general properties" in result['stdout']
        assert "tps-config-show         Show general properties" in result['stdout']

    config_modhelp_output = ansible_module.command('pki tps-config-mod --help')
    config_showhelp_output = ansible_module.command('pki tps-config-show --help')

    for result in config_modhelp_output.values():
        assert "--help            Show help options" in result['stdout']
        assert "--input <file>    Input file containing general properties." in result['stdout']
        assert "--output <file>   Output file to store general properties." in result['stdout']

    for result in config_showhelp_output.values():
        assert "--help            Show help options" in result['stdout']
        assert "--output <file>   Output file to store general properties." in result['stdout']


@pytest.mark.parametrize("certnick,expected", [
    ("TPS_AdminV", TPS_CONFIG_VALUES),
    ("TPS_AgentV", ["ForbiddenException: Authorization Error"]),
    ("TPS_OperatorV", ["ForbiddenException: Authorization Error"]),
])
def test_tpsconfig_show_validnicks(ansible_module, certnick, expected):
    """
    :Title: Test tps-config-show with valid Admin, Agent and Operator certificates

    :Description: Test tps-config-show with valid Admin, Agent and Operator certificates

    :Requirement:

    :Setup: Use pki setup via ansible playbooks

    :Steps: 1. Run tps-config-show with valid Admin Agent and Operator certificates

    :Expectedresults: 1. The command should be able to show the config using valid Admin but should fail for Agent and Operator certificates

    :Automated: Yes
    """
    tpsconfig_show_output = ansible_module.pki(
        cli='tps-config-show',
        nssdb=constants.NSSDB,
        port=constants.TPS_HTTPS_PORT,
        protocol='https',
        certnick=certnick,
    )
    for result in tpsconfig_show_output.values():
        for iter in expected:
            if 'Admin' in certnick:
                assert iter in result['stdout']
            else:
                assert iter in result['stderr_lines']


@pytest.mark.parametrize("certnick,expected", [
    ("TPS_AdminR", REVOKED_CERT_MESSAGE),
    ("TPS_AdminE", EXPIRED_CERT_MESSAGE),
    ("TPS_AgentR", REVOKED_CERT_MESSAGE),
    ("TPS_AgentE", EXPIRED_CERT_MESSAGE),
    ("TPS_OperatorR", REVOKED_CERT_MESSAGE),
    ("TPS_OperatorE", EXPIRED_CERT_MESSAGE),
])
def test_tpsconfigshow_othernicks(ansible_module, certnick, expected):
    """
    :Title: Test tps-config-show with expired and revoked certificates

    :Description: Test tps-config-show with expired and revoked certificates

    :Requirement:

    :Setup: Use pki setup via ansible playbooks

    :Steps: 1. Run tps-config-show with Revoked and Expired certificates

    :Expectedresults: 1. The command should fail with appropriate messages for revoked and expired certificates

    :Automated: Yes
    """
    tpsconfig_show_output = ansible_module.pki(
        cli='tps-config-show',
        nssdb=constants.NSSDB,
        port=constants.TPS_HTTPS_PORT,
        protocol='https',
        certnick=certnick,
    )
    for result in tpsconfig_show_output.values():
        for iter in expected:
            assert iter in result['stderr_lines']
